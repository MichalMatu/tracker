# Security Policy

## Reporting a vulnerability

Please do **not** publish secrets, credentials, private Bluetooth observations, personal data, or exploitable security details in a public issue.

For now, report security-sensitive findings directly to the repository owner through GitHub rather than opening a public issue. Include only the minimum information needed to reproduce the problem.

## Supported builds

The project is currently in active stabilization. Security fixes target the latest `main` and the newest tester release unless a specific older build is explicitly maintained.

## Secrets

GitHub Actions runs automated secret scanning. Contributions must not contain API keys, signing material, tokens, passwords, private certificates, or real user data.
