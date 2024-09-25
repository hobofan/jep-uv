# jep + uv

The goal of this project is to serve as an example of how one could integrate [jep](https://github.com/ninia/jep) with [uv](https://github.com/astral-sh/uv),
to provide a Java -> Python integration that is simple to use for developers and at the same time doesn't require manual setup by users.

One of the challenges I have faced in the past when using `jep`, when using it to build `jar`s that are integrated into larger Java applications,
those applications may not be aware of a Python dependency, and ultimately adding `jep` would force the end-user to install
the correct Python version for usage with `jep`, which is both cumbersome and error-prone over time (as a Python update may break the jep usage.

So the main approach here is to use `uv` to either detect or install a fitting Python version for usage with `jep`.
Additionally, the Python package we want to embed in the `jar` has its dependencies manged by `uv`, and the `venv` created by `venv` install.

## Goals

- Provide a seamless end-user experience by not requiring any manual setup by the end-user
- Provide a good developer experience by:
  - providing a simple way to use a Python package with dependencies (including native dependencies)
  - providing a consistent Python environment with a predictable Python version
- Provide an example project, that can be adjusted in individual parts to support similar use-cases

## Non-goals

- Provide a ready-to-use library that can be plugged into other Java projects (I don't have the maintenance capacity to do that)
- Runtime installation of the package dependencies
  - dependencies are bundled into the `.jar` file at build-time, and extracted at runtime
  - trying to install the dependencies at runtime would become very difficult with native dependencies, as build tools would need to be present on the end-users machine
- Cross-compilation/multi-platform support (e.g. building a windows-compatible `.jar` on a linux machine)
  - Again due to native dependencies, this could become very difficult to achieve
