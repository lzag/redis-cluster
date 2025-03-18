# Automated Redis cluster setup with Docker & Benchmark

[Read the post](https://lukaszzagroba.com/automating-redis-cluster-setup/)

## Usage

This will start a 3-node Redis cluster with 1 replica per node.
Cluster is spun up automatically and the script waits for the cluster to be ready before passing the healthcheck.

```bash
docker compose -f docker-compose.yml up
```
There's also a setup with host networking
```bash
docker compose -f docker-compose.host.yml up
```

## Motivation

The motivation for this work stems from my interest in distributed systems and resilient architectures. Redis is renowned for its performance as an in-memory data store, but its clustering feature offers a pathway to scalability and fault tolerance that standalone instances cannot achieve. This project is an experiment in harnessing Redis clustering to distribute data across nodes, manage failures, and simplify deployment through automation. By creating this setup, I aimed to explore Redis’ advanced features—such as sharding, replication, and dynamic scaling—while ensuring the process remains accessible and repeatable.

## Benchmarks

Redis performance benchmarks comparing Vert.x (Kotlin with coroutines) and PHP (Swoole) in standalone and cluster modes.

```bash
docker compose -f docker-compose.bench.yml up vertx-standalone-benchmark
```
```bash
docker compose -f docker-compose.bench.yml up vertx-cluster-benchmark
```
```bash
docker compose -f docker-compose.bench.yml up php-standalone-benchmark
```
```bash
docker compose -f docker-compose.bench.yml up php-cluster-benchmark
```