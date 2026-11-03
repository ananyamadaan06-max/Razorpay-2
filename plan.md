1. Requirements 
Build an in-process Feature Flag Service supporting: 

-> Boolean, String, and integer flags
-> Attribute based targeting rules 
-> Percentage rollout with sticky bucketing 
-> Shared and independent bucketing across flags
-> Environment isolation (dev, staging, prod)
-> Live configuration update without restarts 
-> Low - latency and synchronous evaluation 
-> Default-on-error with structured logging 
-> Unit tests and incremental git commits 

2. Tech Stack

-> Java, Maven, JUnit 5. 
-> ConcurrentHashMap for in-memory storage
-> Deterministic bucketing - SHA - 256 

3. Design Decisions 

-> Evaluation order : Targeting -> rollout -> Default
-> First matching targeting rule wins 
-> Independent bucket key : env+tenant+user+flagName
-> Shared bucket key : env+ tenant+user+BucketGroup 
-> Use 10,000 buckets with stable SHA-256 hashing 
-> Reject invalid percentage and value types during configuration 
-> Use immutable configurations and atomic replacement for live updates 
-> Missing rollout identity returns default 
-> Missing flag returns caller fallback 
-> Evaluation errors return flag default and log structured error

4. Core Components 
-> FlagConfig : flagName, type, default, rules, rollout 
-> TargetingRules : condition, priority, return value 
-> RolloutConfig : percentage, value, bucket group 
-> inMemoryConfigStore : thread-safe storage 
-> ConfigStore : set() and get() methods 
-> FlagEvaluator : applies targeting, rollout, and default 
-> BucketHasher -> deterministic hashing 

5. Evaluation flow 
-> Fetch flag configuration for the environment 
-> Evaluate targeting rules in order
-> If matched, return rule value
-> Otherwise calculate percentage rollout bucket
-> If selected, return rollout value 
-> otherwise, return the default value
-> On internal error, return default and log error 


6. Implementation Checkpoints 

CP0 - Setup 
-> Initialized Java Maven project
-> Adding JUnit5 and logging 
-> Initialize Git 

Test: Build and smoke test pass 

CP1 - Models and Config Store 
-> Implements models and validate 
-> Implement thread-safe config stores 
-> Support enviromentatl isolation 

Tests : All flag types, set/get, invalid config and enviromental isolation test 

CP2 - Targeting and Evaluation 
-> Implement the evaluation service 
-> Add EQUALS and IN targeting rules 
-> Apply first - match precedence and defaults 

Tests: Rules match,miss, multiple rules, all flag types and fallback 

CP3 - Percentage Rollouts 

-> Implement deterministic hashing SHA - 256 bucketing 
-> Support shared and independent bucket key 
-> Integrate rollouts with evaluation 

Test : 0%, 50%, 100%, stickiness, shared buckets, tenant isolation, and distribution 

CP4 - Live Updates and Safety 
-> Implement atomic configuration replacement
-> Structured error logging 
-> Ensure thread - safety and usage 

Tests : Live updates, invalid updates, concurrent readers/writes and default on errors 

CP5 - Integration 

-> Add end-to-end tests 
-> Create a runnable demo 

Test: Complete regression suite and a demo pass 