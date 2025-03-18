package com.lzag.redisbenchmark

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisClientType
import io.vertx.redis.client.RedisOptions
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class MainVerticle : AbstractVerticle() {
    override fun start(startPromise: Promise<Void>) {
        println("Deploying benchmark verticle")
        vertx.deployVerticle(RedisBenchmarkVerticle())
            .onSuccess {
                println("Benchmark complete")
                startPromise.complete()
            }
            .onFailure { startPromise.fail(it) }
    }
}

class RedisBenchmarkVerticle : CoroutineVerticle() {
    private lateinit var redis: RedisAPI
    private val totalOps = 1_000_000
    private val concurrentUsers = 100
    private val opsPerUser = totalOps / concurrentUsers

    override suspend fun start() {
        val options = if (System.getenv("MODE") == "cluster") {
            RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .setMaxPoolWaiting(150)
                .setMaxPoolSize(100)
                .addConnectionString("redis://redis1:6379")
        } else {
            RedisOptions()
                .setMaxPoolWaiting(150)
                .setMaxPoolSize(100)
                .addConnectionString("redis://redis:6379")
        }
        try {
            val client = Redis.createClient(vertx, options)
            redis = RedisAPI.api(client)
            runBenchmark()
        } catch (e: Exception) {
            println("Failed to execute benchmarks: ${e.message}")
            throw e
        }
    }

    override suspend fun stop() {
        redis.close()
    }

    private suspend fun runBenchmark() {
        println("Starting benchmark")
        val totalTimeMs = measureTimeMillis {
            val jobs = (1..concurrentUsers).map { userId ->
                launch { simulateUser(userId) }
            }
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
                redis.set(listOf(key, value)).coAwait()
                opsCompleted++
            } catch (e: Exception) {
                println("User $userId failed on key $key: ${e.message}")
            }
        }
        println("User $userId completed $opsCompleted operations")
    }
}

class RedisBenchmark {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val vertx = Vertx.vertx()
            vertx.deployVerticle(MainVerticle())
                .onSuccess { deploymentId ->
                    vertx.undeploy(deploymentId) {
                        if (it.succeeded()) vertx.close() else println("Undeploy failed: ${it.cause().message}")
                }
            }
        }
    }
}
