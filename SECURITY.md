# Reporting a security vulnerability

Use [GitHub private vulnerability reporting](https://github.com/nonomnonom/blobs/security/advisories/new) to report a suspected vulnerability privately. Public issues are suitable for ordinary bugs; keep vulnerability details in the private report until disclosure is coordinated.

Include the affected release or commit, Android version or host environment, reproduction steps, the affected file or feature, and the observed impact. A small synthetic input is useful for parser or package-validation reports. Remove personal conversations, credentials and unrelated private data from logs and examples.

Reports are reviewed against the latest published release and the `main` branch. The maintainer may request more information or confirmation on a current build, coordinate a fix, and publish an advisory when appropriate. There is no guaranteed response deadline.

Blobs performs inference and retrieval locally. Network access is used for user-requested asset downloads and signed download-list checks. Relevant security boundaries include untrusted document/model parsing, package integrity, update signatures, local storage, exports and native runtime behavior. See [the architecture guide](docs/architecture.md) for the implementation and [Contributing](CONTRIBUTING.md) for development checks.
