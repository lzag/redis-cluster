import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisOptions
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class RedisBenchmarkVerticle : CoroutineVerticle() {

    private lateinit var redis: RedisAPI
    private val totalOps = 100_000
    private val concurrentUsers = 100
    private val opsPerUser = totalOps / concurrentUsers // 1,000 ops per user

    override suspend fun start() {
        // Configure Redis connection (standalone or cluster)
        val options = RedisOptions().apply {
            connectionString = "redis://localhost:6379" // Adjust for cluster: "redis://host1:7000,host2:7001,..."
        }

        val client = Redis.createClient(vertx, options)
        val connection = client.connect().await() // Suspend until connected
        redis = RedisAPI.api(connection)

        println("Connected to Redis. Starting benchmark...")
        runBenchmark()
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
        vertx.close()
    }

    private suspend fun simulateUser(userId: Int) {
        for (i in 1..opsPerUser) {
            val key = "user${userId}_key$i"
            val value = "value$i"
            try {
                redis.set(listOf(key, value)).await() // Suspend until SET completes
                // Uncomment for mixed ops (e.g., 33% SET, 33% GET, 33% DEL)
                // redis.get(key).await()
                // redis.del(listOf(key)).await()
            } catch (e: Exception) {
                println("User $userId failed on key $key: ${e.message}")
            }
        }
        println("User $userId completed $opsPerUser operations")
    }
}

fun main() {
    val vertx = Vertx.vertx()
    // Deploy 4 instances for more threads
    vertx.deployVerticle(
        RedisBenchmarkVerticle(),
        DeploymentOptions().setInstances(4)
    )
}
