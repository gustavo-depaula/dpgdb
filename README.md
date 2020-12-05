<div align="center">
  <img width="50%" src=".github/logo.png" alt="dpgdb logo">

  <h1><code>dpgdb</code></h1>

  <p>
    <em><b>d</b>e<b>p</b>aula<b>g</b>u's simple <b>d</b>ata<b>b</b> implementation, written, with love, in clojure! ❤️</em>
  </p>
</div>

## 🏃‍ TLDR

This is an implementation of a key-value database that uses an append-only log file as its permanent storage and an in-memory hash-table to optmize reads. This system is described in the book "Designing data-intensive applications".

## 🚀 Installation & Usage

This project uses [Leiningen](https://leiningen.org/) to manage dependencies and automation.
You can use [brew](https://formulae.brew.sh/formula/leiningen) or another lesser package manager to install it.

This project uses this version of Leiningen (`lein --version`):

```bash
Leiningen 2.9.1 on Java 1.8.0_192 OpenJDK 64-Bit Server VM
```

Having `leiningen` installed, run the following command to install the dependencies.

```bash
lein deps
```

Then, you can run it with:

```bash
lein run
```

## 📘 Brief documentation

The system is comprised of two main parts:

- the `db-files` directory, where our logs live
  - here, we define logs as an append-only sequence of records
  - to prevent having one big file that is slow to parse, we divide our logs into multiple files; we call these files "segments"
- the memcache, our in-memory data structures that indexes our database
  - we use two clojure atoms here, each one keeping a dictionary
    - `entries-refs`, keeps where each entry in our database is located (which file nd which line)
    - `segment-counter` is responsible for keeping track of the size of the segments files

The system can perform 4 actions:

- `set`:

  - saves a new key-value entry into the database
  - it does this by appending the information into a segment file and saving the written location into the `entries-refs` atom and updating the `segment-counter`
  - `set key value`, e.g. `set myAge 20`

- `get`:

  - retrieve a value given a key
  - it does this by _derefing_ the `entries-refs` atom and reading the specified location
  - `get key`, e.g. `get myAge`

- `compact`:

  - given the nature of how database is structured, we will have duplicated records if a key is set multiple times
  - the compacting process cleans our segments files by keeping the last (thus current) value of the key
  - as we only do "append" procedures, we create a new segment file with the compacted segment and delete the old one
  - `compact segment-index`, e.g. `compact 0`

- `merge`:
  - as we only do "append" procedures, the database will naturally create multiple files
  - merge creates a new compacted file resulting from the merge of two or more segments, deleting the old files
  - the database automatically merges all segments when it starts
  - `merge ...indexes`, e.g. `merge 0 1 2`

## 📝 TODO List

- [ ] Write automated tests 😭
- [ ] Evolve this to use SSTables 🗄
- [ ] Making it an LSM-Tree 🌳
