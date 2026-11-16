# Local Java dependency and runtime assessment

Assessed 16 November 2026 using the actual **209-test packaged JAR** and a local Linux image built from the source Dockerfile's runtime stage. No hosted service, deployment settings, production credentials or customer records were used. This is an advisory/database snapshot, not a guarantee that every vulnerability is known or that the hosted service matches these artifacts.

## Actual scanner results

| Target | Package/advisory matches | Unique advisories | Critical | High | Medium | Low |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Packaged Java application | 0 | 0 | 0 | 0 | 0 | 0 |
| Original Alpine runtime | 38 | 17 | 1 | 4 | 21 | 12 |
| Remediated runtime | 3 | 1 | 0 | 0 | 3 | 0 |

The Java CycloneDX SBOM contains **83 components: the application plus 82 nested runtime JARs**. The runtime SBOM contains 1,239 components, including 1,117 file entries and 121 versioned package URLs; these are not 1,239 installed packages. It identifies OpenJDK 21.0.13+9-LTS as well as Java dependencies and installed image packages. Reports retain unfixed findings; no local ignore/VEX rule, `--only-fixed` filter or vulnerability suppression was added. Grype's built-in Linux-header matching exclusions were unchanged.

Evidence: [Java findings](verification/backend-grype.json), [Java SBOM](verification/backend-runtime.cdx.json), [original image findings](verification/runtime-grype.json), [remediated findings](verification/runtime-remediated-grype.json), [remediated SBOM](verification/runtime-remediated.cdx.json), [assessment metadata](verification/dependency-assessment.json), [runtime identity](verification/runtime-remediated-image.json), and [actual container smoke](verification/runtime-smoke.json).

## Concrete runtime changes

- `apk upgrade --no-cache` takes the Alpine 3.24 vendor fix from **zlib 1.3.2-r1 to 1.4-r0**. The original scanner rated CVE-2026-4738 Critical. The [Alpine tracker](https://security.alpinelinux.org/vuln/CVE-2026-4738) identifies 1.4-r0 as fixed. Upstream's advisory concerns contributed `infback9` code, so the original package match alone did not prove an application attack path; updating the vendor package avoids relying on a blanket false-positive decision.
- Remove unused runtime **GnuPG** through APK's dependency solver. The installed dependency graph showed `sqlite-libs` required only by GnuPG's `gpg`/`gnupg-keyboxd`; removal drops that entire unused chain. The application uses no system GnuPG process. Its direct Java entrypoint already overrides the upstream helper entrypoint; that behavior is unchanged. Java's CA/font/JRE dependencies remain installed.
- This removes the installed **SQLite 3.53.4-r0** matches, including four High findings: CVE-2026-119586, CVE-2026-119379, CVE-2026-119374 and CVE-2026-119373. The CLI `.recover` issue did not establish library reachability, but [Alpine's build](https://raw.githubusercontent.com/alpinelinux/aports/3.24-stable/main/sqlite/APKBUILD) enables session/FTS5, so the other affected code could not be dismissed as disabled. No supported fixed package was reported. Removal makes those unused installed components absent instead of suppressing their findings.
- Remove unused **GNU coreutils** and its orphan dependencies. BusyBox utilities remain. This eliminates three unique Medium advisories repeated across coreutils subpackages: CVE-2016-2781, CVE-2026-56391 and CVE-2026-56392. The Java application does not execute those tools. The [upstream Dockerfile](https://raw.githubusercontent.com/adoptium/containers/main/21/jre/alpine/3.24/Dockerfile) installs them for its certificate helper, which this project's existing Java entrypoint already bypasses. Restoring that helper later would require restoring/reviewing its dependencies; custom certificate import remains an explicit hosting verification requirement.
- Set explicit numeric **USER 10001:10001**, with a root-owned read-only application JAR. `/tmp` remains writable for multipart handling; no local application upload writer was found. Cloudinary remains the configured image integration.
- Add `.dockerignore` to exclude Git metadata, environment files, IDE files, logs, verification artifacts and `target/`. The local scanner cache alone exceeds 3 GB; it must not enter a source build context.

These changes do not alter the application JAR, business rules, LKR 350 delivery, historical totals or database data. They do not rotate any credentials.

## Remaining runtime advisory

**CVE-2025-60876 remains Medium in BusyBox 1.37.0-r31**, producing three package matches (`busybox`, `busybox-binsh`, `ssl_client`). The [official Alpine tracker](https://security.alpinelinux.org/vuln/CVE-2025-60876) marks the installed 3.24 package as possibly vulnerable and lists no fixed vendor release. [Alpine's configuration](https://raw.githubusercontent.com/alpinelinux/aports/3.24-stable/main/busybox/busyboxconfig) enables `wget`.

The reported attack requires an attacker-influenced URL to be passed to the BusyBox `wget` applet. Review found a direct Java container entrypoint, no `wget` health check, and no `ProcessBuilder`, `Runtime.exec` or system-wget application path. That is a bounded reachability assessment, not proof that the binary is fixed or that every operator path is safe. Do not feed untrusted URLs to runtime shell tools. Keep this finding visible for informed runtime/security review, a supported vendor patch or a separately tested image change before the final release decision. No automatic security sign-off follows from these scans.

## Tools, identities and reproduction

- Official [Grype 0.121.3](https://github.com/anchore/grype/releases/tag/v0.121.3), released 9 November 2026, embeds Syft 1.57.1. The Windows AMD64 release ZIP SHA-256 was verified against the official release checksum: `3007a5afea25c60030a49d6c24d4e5abb6c94c0dbeed7ea303eb26df8e900c18`.
- Actual valid advisory DB: **schema v6.1.10, built 2026-11-16T06:30:54Z**. The dated URL in metadata identifies the downloaded archive; the DB's own built timestamp is recorded separately. All final passes used that same current valid database.
- Tested JAR SHA-256: `1977b3ab4a4a5ff4c061f4a2dc03ceb6c564de9d91c5a4677e15a163fd336433`.
- Remediated local image/index digest: `sha256:2697a7fea6925137a8494fc859670669634c67cf96b5a00cdec3a78f39eec6e8`.
- Its Linux image-config digest observed by Grype: `sha256:862770de8a6f13395ebfe3284b3d5f12123b5828ff4f8c1513a0adfa4b78a3a9`. Image indexes, platform manifests and configuration blobs are different objects; their hashes must not be conflated.
- Runtime base resolved by BuildKit: `eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474`. Actual runtime Java is **Temurin 21.0.13+9-LTS**.

Install the verified native tool outside tracked source (default ignored location `target/security-tools/grype-0.121.3/grype.exe`). An approved process-scoped proxy may be needed for public downloads in this environment; no global proxy settings were changed. The assessment runner bounds Go memory and keeps advisory/temp/archive files under ignored `target/`.

```powershell
# Against the already built reviewed image; default refreshes the advisory DB.
.\scripts\assess-runtime.ps1 -RuntimeImage sekkin-backend-runtime-remediated:20261116
# Use -UseCachedDatabase only to reproduce with an already current valid DB.
.\scripts\smoke-runtime.ps1 -Image sekkin-backend-runtime-remediated:20261116
```

The local image was built by copying the exact tested JAR into a temporary ignored context and using the source runtime-stage instructions verbatim, changing only the multi-stage `COPY --from=build` to a local `COPY`. The full source Dockerfile Maven builder was **not** executed or scanned in this verification. In particular, these results do not assess the builder's Maven 3.9.6 image or prove that a subsequent source build produces these exact bytes. Before release, build the final approved source revision in the actual controlled pipeline, inspect its JAR/runtime digest and rerun assessment/smoke on that final image. Mutable upstream tags/packages may change between builds.

To reproduce the bounded runtime-stage build from this backend checkout:

```powershell
$taskRoot = (Get-Location).Path
$taskContext = Join-Path $taskRoot 'target/runtime-remediation-context'
New-Item -ItemType Directory -Force -Path $taskContext | Out-Null
Copy-Item -LiteralPath 'target/cosmetic-ecommerce-backend-0.0.1-SNAPSHOT.jar' -Destination (Join-Path $taskContext 'app.jar')
$taskSource = [IO.File]::ReadAllText((Join-Path $taskRoot 'Dockerfile'))
$taskRuntime = $taskSource.Substring($taskSource.IndexOf('FROM eclipse-temurin:21-jre-alpine'))
$taskRuntime = $taskRuntime.Replace('COPY --from=build --chmod=0444 /app/target/*.jar app.jar', 'COPY --chmod=0444 app.jar app.jar')
[IO.File]::WriteAllText((Join-Path $taskContext 'Dockerfile'), $taskRuntime, (New-Object Text.UTF8Encoding($false)))
docker build --tag sekkin-backend-runtime-remediated:20261116 $taskContext
```

If this network requires a proxy, configure the approved proxy in the invoking process and pass Docker's predefined `HTTP_PROXY`/`HTTPS_PROXY` build arguments. Do not put credentials into a Dockerfile, image environment, report or tracked file. Rebuilding may produce a new image/index digest; assess and smoke the image actually built rather than reusing an older digest's claims.

## Actual hosting verification still required

The container smoke uses another disposable PostgreSQL 17.12 instance, the synthetic legacy schema and the exact manual migration. It publishes only the application HTTP port to loopback; PostgreSQL is confined to the labelled task network. It deliberately uses trust/no TLS with synthetic signing material and removes only its owned resources.

The final smoke passed **15 checks**, including actual embedded-server guest COD creation/retry, fixed LKR 350/total 1,350, one stock decrement, preserved historical zero delivery, anonymous admin denial, CORS, non-root identity, read-only JAR and installed vendor-fixed zlib. Its image identity and running JAR hash match the final scan metadata. Earlier harness-only attempts stopped on PowerShell native stderr handling and an APK-info format assumption; both were corrected before this completed run, and each attempt removed its owned containers/network.

The source accepts an externally supplied JDBC URL. Local success therefore does not verify Supabase's actual schema/version, TLS certificate/hostname validation, Render environment/profile overrides, proxy forwarding or effective rate-limit source, domain/CORS ownership, external Cloudinary uploads, or courier settlement evidence. Those checks require approved isolated provider accounts and operator review. Credential containment/rotation and existing administrator review remain urgent separate operator work; business approval is not a reason to defer them.
