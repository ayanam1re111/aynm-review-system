# UrbanPicks
UrbanPicks is a high-concurrency local life service platform built with Spring Boot and Redis, focusing on cache optimization, distributed concurrency control, and high-performance coupon flash sale scenarios.

## Tech Stack
- Backend: Spring Boot, Spring MVC, MyBatis-Plus
- Cache: Redis (String, Hash, SortedSet, GEO, Lua scripts)
- Distributed & Concurrency: Redisson distributed lock, thread pool, asynchronous processing
- Database: MySQL
- Tools: Lombok, Hutool, Jackson

## Core Features
- User authentication with SMS verification codes
- Merchant information query and caching optimization
- High-concurrency coupon flash sales with overselling prevention
- Review, likes, ranking and dynamic feed flow
- User check-in, follow system and nearby merchant search

## Technical Highlights
- Solved cache penetration, cache breakdown, and cache avalanche
- Implemented cache preheating and logical expiration for hot data
- Used distributed locks and Lua scripts to ensure atomic operations
- Adopted asynchronous processing to improve system throughput

## Environment Requirements
- JDK 8 or later
- MySQL 5.7 or later
- Redis 5.0 or later
- Maven 3.6 or later

## Getting Started
1. Import the SQL script to initialize the database
2. Configure database and Redis connections in application.yml
3. Start the Redis service
4. Run the main application class to launch the project
