#!/usr/bin/env php
<?php

use Swoole\Coroutine;
use Swoole\Runtime;
use Swoole\Coroutine\Channel;

function runBenchmark(string $mode): void
{
    $totalOps = 1_000_000;
    $concurrentUsers = 100;
    $opsPerUser = $totalOps / $concurrentUsers;

    echo "Starting Redis benchmark in $mode mode...\n";

    $startTimes = [];
    $endTimes = [];

    $startTime = microtime(true);
    Runtime::enableCoroutine();
    Coroutine\run(function () use ($mode, $concurrentUsers, $opsPerUser, &$startTimes, &$endTimes) {
        $standaloneConfig = ['host' => 'redis', 'port' => 6379];
        // $clusterConfig = [
            // ['host' => 'redis_1', 'port' => 6379],
            // ['host' => 'redis_2', 'port' => 6379],
            // ['host' => 'redis_3', 'port' => 6379],
            // ['host' => 'redis_4', 'port' => 6379],
            // ['host' => 'redis_5', 'port' => 6379],
            // ['host' => 'redis_6', 'port' => 6379],
        // ];
        $clusterConfig = ["redis_1:6379", "redis_2:6379", "redis_3:6379", "redis_4:6379", "redis_5:6379", "redis_6:6379"];

        // Initialize connection pool
        $poolSize = $concurrentUsers;
        $config = $mode === 'standalone' ? $standaloneConfig : $clusterConfig;
        $redisPool = new Channel($poolSize);

        if ($mode === 'standalone') {
            for ($i = 0; $i < $poolSize; $i++) {
                $redis = new Redis();
                try {
                    $redis->connect($config['host'], $config['port']);
                    $redisPool->push($redis);
                    echo "Initialized Redis connection #$i ($mode mode)\n";
                } catch (\Throwable $e) {
                    echo "Failed to init connection #$i: {$e->getMessage()}\n";
                    exit(1);
                }
            }
        } else {
            for ($i = 0; $i < $poolSize; $i++) {
                $redis = new RedisCluster(NULL, $clusterConfig, 1.5, 1.5, true);
                try {
                    // Connect to one node; PhpRedis will discover cluster
                    // $redis->connect($clusterConfig[0]['host'], $clusterConfig[0]['port']);
                    // $redis->setOption(Redis::OPT_CLUSTER, Redis::CLUSTER_REDIS);
                    $redisPool->push($redis);
                    echo "Initialized Redis connection #$i ($mode mode)\n";
                } catch (\Throwable $e) {
                    echo "Failed to init connection #$i: {$e->getMessage()}\n";
                    exit(1);
                }
            }
        }

        for ($userId = 1; $userId <= $concurrentUsers; $userId++) {
            Coroutine\go(function () use ($userId, $redisPool, $opsPerUser, &$startTimes, &$endTimes) {
                $redis = $redisPool->pop();
                $startTimes[$userId] = microtime(true);
                simulateUser($userId, $redis, $opsPerUser);
                $endTimes[$userId] = microtime(true);
            });
        }

        $redisPool->close();
    });

    $totalTimeSec = microtime(true) - $startTime;
    $throughput = $totalOps / $totalTimeSec;
    for ($userId = 1; $userId <= $concurrentUsers; $userId++) {
        $elapsedTimeSec = $endTimes[$userId] - $startTimes[$userId];
        echo "User $userId started {$endTimes[$userId]} ended {$endTimes[$userId]} completed in $elapsedTimeSec seconds\n";
    }

    echo "Completed $totalOps operations in $totalTimeSec seconds ($throughput ops/sec)\n";
}

function simulateUser(int $userId, Redis|RedisCluster $redis, int $opsPerUser): void
{
    $opsCompleted = 0;
    for ($i = 1; $i <= $opsPerUser; $i++) {
        $key = "user{$userId}_key{$i}";
        $value = "value{$i}";
        try {
            $redis->set($key, $value);
            $opsCompleted++;
        } catch (\Throwable $e) {
            echo "User $userId failed on key $key: {$e->getMessage()}\n";
        }
    }
    echo "User $userId completed $opsCompleted operations\n";
}

$mode = $argv[1] ?? 'standalone';
if (!in_array($mode, ['standalone', 'cluster'])) {
    echo "Usage: php redis_benchmark.php [standalone|cluster]\n";
    exit(1);
}

runBenchmark($mode);
