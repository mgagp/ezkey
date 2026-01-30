# Plan: Add Device Simulation to EZKey CLI Python

Add device simulation commands to ezkey-cli-python enabling enrollment binding/verification and authentication pending/respond flows, mimicking the demo-device behavior directly from the command line. This provides developers an OpenSSH-like client experience for testing authentication flows without building a full device application.

## Steps

1. **Create device storage module** in [ezkey-cli-python/ezkey/device_storage.py](ezkey-cli-python/ezkey/device_storage.py) to manage `~/.ezkey/devices/{enrollment_id}.json` files, storing enrollment ID, proof tokens, device keypair, integration public key, and metadata following the demo-device pattern from [EnrollmentStoreService.java](ezkey-demo-device/src/main/java/org/ezkey/demo/device/service/EnrollmentStoreService.java).

2. **Implement enrollment commands** in [ezkey-cli-python/ezkey/commands/device.py](ezkey-cli-python/ezkey/commands/device.py) with `ezkey device enroll --enrollment-id X --enrollment-proof-token Y --challenge Z` orchestrating BIND → crypto keypair → crypto sign → VERIFY flow using [auth_api.py](ezkey-cli-python/ezkey/auth_api.py) POST `/api/v1/enrollments/bind` and `/api/v1/enrollments/verify` endpoints per [Postman collection](postman/collections/v2.1/EZ Key Enrollments auth.postman_collection.json).

3. **Implement authentication commands** in [device.py](ezkey-cli-python/ezkey/commands/device.py) with `ezkey device auth --enrollment-id X [--approve|--deny]` for single-shot execution: generate device proof token, call PENDING (POST `/api/v1/auth-attempts/pending`), validate integration signature, sign auth attempt proof token, call RESPOND (POST `/api/v1/auth-attempts/respond`) per [Postman collection](postman/collections/v2.1/EZ Key Auth Attempts auth.postman_collection.json).

4. **Add device management commands** including `ezkey device list` displaying enrolled devices table, `ezkey device show --enrollment-id X` showing full JSON details, and `ezkey device remove --enrollment-id X` deleting enrollment with confirmation prompt.

5. **Integrate with CLI main** by registering device command group in [cli.py](ezkey-cli-python/ezkey/cli.py), adding brief security warning to `device enroll` command help text noting plaintext key storage for development/testing purposes.

## Further Considerations

1. **Error handling**: Should the CLI display user-friendly error messages when PENDING returns 204 (no pending attempts) vs connection errors vs authentication failures? Recommend distinct messages for each scenario.

2. **Crypto API dependency**: Commands assume Docker stack running on localhost:9090 for crypto operations. Should CLI check crypto API availability before device operations or fail gracefully during execution? Suggest validation in enrollment command only.
