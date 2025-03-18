#!/bin/sh
set -e

# Default Redis port
REDIS_PORT=${REDIS_PORT:-6379}
# Cluster nodes (e.g., "redis-1:6379 redis-2:6379 ...")
CLUSTER_NODES=${CLUSTER_NODES:-""}
# Number of replicas per master (default 0)
CLUSTER_REPLICAS=${CLUSTER_REPLICAS:-0}
# Path to initialization flag
INIT_FLAG="/data/.initialized"

# Function to wait for local Redis to be ready
wait_for_redis() {
    echo "Waiting for local Redis to be ready on port $REDIS_PORT..."
    for i in $(seq 1 30); do
        if redis-cli -h localhost -p "$REDIS_PORT" PING | grep -q "PONG"; then
            echo "Local Redis is ready."
            return 0
        fi
        echo "Attempt $i: Redis not ready yet. Retrying in 1 second..."
        sleep 1
    done
    echo "Local Redis failed to start after 30 seconds."
    exit 1
}

wait_for_cluster() {
    echo "Waiting for cluster to be ready..."
    for i in $(seq 1 30); do
        if redis-cli -h "${CLUSTER_NODES%% *}" CLUSTER INFO | grep -q "cluster_state:ok"; then  # Check first node
            echo "Cluster ready."
            return 0
        fi
        sleep 1
    done
    echo "Cluster failed to stabilize after 30s."
    exit 1
}

validate_node_count() {
    NODE_COUNT=$(echo "$CLUSTER_NODES" | wc -w)  # Number of nodes in CLUSTER_NODES
    MIN_MASTERS=3  # Minimum number of masters for a Redis cluster
    REQUIRED_NODES=$((MIN_MASTERS + MIN_MASTERS * CLUSTER_REPLICAS))  # Total nodes needed

    if [ "$NODE_COUNT" -lt "$MIN_MASTERS" ]; then
        echo "Error: At least $MIN_MASTERS nodes are required for a Redis cluster. Got $NODE_COUNT."
        exit 1
    fi

    if [ "$NODE_COUNT" -lt "$REQUIRED_NODES" ]; then
        echo "Error: With $CLUSTER_REPLICAS replicas per master, at least $REQUIRED_NODES nodes are needed (3 masters + $((MIN_MASTERS * CLUSTER_REPLICAS)) replicas). Got $NODE_COUNT."
        exit 1
    fi

    EXPECTED_TOTAL=$((MIN_MASTERS * (1 + CLUSTER_REPLICAS)))
    if [ "$NODE_COUNT" -ne "$EXPECTED_TOTAL" ]; then
        echo "Warning: $NODE_COUNT nodes provided, but $EXPECTED_TOTAL expected for $MIN_MASTERS masters with $CLUSTER_REPLICAS replicas each. Extra nodes may not be used."
    fi

    echo "Node count validated: $NODE_COUNT nodes, $CLUSTER_REPLICAS replicas per master."
}

create_and_configure_cluster() {
    echo "Creating Redis cluster with nodes: $CLUSTER_NODES... and $CLUSTER_REPLICAS replicas per master"
    for i in $(seq 1 5); do
        redis-cli --cluster create $CLUSTER_NODES --cluster-replicas "$CLUSTER_REPLICAS" --cluster-yes && break
        echo "Attempt $i failed. Retrying in 2 seconds..."
        sleep 2
    done
    if [ $? -eq 0 ]; then
        echo "Cluster created successfully."
        touch "$INIT_FLAG"
    else
        echo "Failed to create cluster after retries."
        exit 1
    fi
}

if [ -n "$CLUSTER_NODES" ] && [ ! -f "$INIT_FLAG" ]; then
    # running in background
    (
        wait_for_redis
        validate_node_count
        create_and_configure_cluster
        wait_for_cluster
    ) &
fi

exec redis-server /redis.conf --port $REDIS_PORT
