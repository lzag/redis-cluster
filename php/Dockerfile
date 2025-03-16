FROM php:8.4-cli

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    libcurl4-openssl-dev \
    libssl-dev \
    libbrotli-dev \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install PHP extensions
RUN docker-php-ext-install pcntl

# Install Redis extension via PECL
RUN pecl install redis && docker-php-ext-enable redis

# Install Swoole
RUN pecl install swoole && docker-php-ext-enable swoole

# Set working directory
WORKDIR /app

# Entrypoint to run PHP
ENTRYPOINT ["php"]
CMD ["--help"]
