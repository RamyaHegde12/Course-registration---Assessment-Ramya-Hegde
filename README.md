# Java_ramya12

This repository contains Java practice code and a bounded course-registration
service example.

## Run the registration tests

Requires a JDK 9 or newer (the code targets Java 8):

```sh
./run-tests.sh
```

The script compiles the service and its dependency-free test harness in a
temporary directory, runs the focused tests, and removes the build output.

See [DESIGN.md](DESIGN.md) for the service boundary, assumptions, and known
limitations, and [AI_USAGE.md](AI_USAGE.md) for the engineering prompt and
verification record.
