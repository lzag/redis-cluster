docker run --rm redis:alpine

docker compose -f docker-compose.host.yml up

/**
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
gaiaadm/pumba netem --interface enp5s0 \
--tc-image gaiadocker/iproute2 \
--duration 5m \
delay --time 500 --jitter 0 \
"re2:redis-cluster-host-redis_.*"
*/
