import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisClientType
import io.vertx.redis.client.RedisOptions
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class MainVerticle : AbstractVerticle() {
    override fun start() {
        println("Deploying RedisBenchmarkVerticle with 4 instances...")
        vertx.deployVerticle(
            RedisBenchmarkVerticle::class.java,
            DeploymentOptions().setInstances(4)
        ) { result ->
            if (result.succeeded()) {
                println("Successfully deployed RedisBenchmarkVerticle instances")
            } else {
                println("Failed to deploy RedisBenchmarkVerticle: ${result.cause().message}")
            }
        }
    }
}
/**
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
gaiaadm/pumba netem --interface enp5s0 \
--tc-image gaiadocker/iproute2 \
--duration 5m \
delay --time 500 --jitter 0 \
"re2:redis-cluster-host-redis_.*"
*/

class RedisBenchmarkVerticle : CoroutineVerticle() {
    private lateinit var redis: RedisAPI
    private val totalOps = 1_000
    private val concurrentUsers = 100
    private val opsPerUser = totalOps / concurrentUsers // 10,000 ops per user

    override suspend fun start() {
        // Standalone
        // val client = Redis.createClient(vertx, "redis://127.0.0.1:6379")
        // Clustered
        val options = RedisOptions()
            .setType(RedisClientType.CLUSTER)
            .setMaxPoolWaiting(150)
            .setMaxPoolSize(50)
            .setHashSlotCacheTTL(60000)
            .addConnectionString("redis://127.0.0.1:6380")
            .addConnectionString("redis://127.0.0.1:6381")
            .addConnectionString("redis://127.0.0.1:6382")
        val client = Redis.createClient(vertx, options)

        redis = RedisAPI.api(client) // Using await() as coAwait() is deprecated
        runBenchmark()
    }

    override suspend fun stop() {
        redis.close()
    }

    private suspend fun runBenchmark() {
        val totalTimeMs = measureTimeMillis {
            // Launch 100 concurrent "users" as coroutines
            val jobs = (1..concurrentUsers).map { userId ->
                launch {
                    simulateUser(userId)
                }
            }
            // Wait for all users to complete
            jobs.forEach { it.join() }
        }

        val totalTimeSec = totalTimeMs / 1000.0
        val throughput = totalOps / totalTimeSec
        println("Completed $totalOps operations in $totalTimeSec seconds ($throughput ops/sec)")
    }

    private suspend fun simulateUser(userId: Int) {
        var opsCompleted = 0
        for (i in 1..opsPerUser) {
            val key = "user${userId}_key$i"
            val value = "value$i"
            try {
                redis.set(listOf(key, value)).coAwait() // Suspend until SET completes
                // Uncomment for mixed ops
                // redis.get(key).await()
                // redis.del(listOf(key)).await()
                opsCompleted++
            } catch (e: Exception) {
                println("User $userId failed on key $key: ${e.message}")
                opsCompleted--
            }
        }
        println("User $userId completed $opsCompleted operations")
    }
}

fun main() {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(MainVerticle()) // Deploy the MainVerticle
}
