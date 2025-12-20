# Advanced Build Optimization Strategies

**Status**: Future improvements (Phase 2+)  
**Estimated Additional Savings**: 30-50% on top of Phase 1

---

## 🚀 Phase 2: Parallel Job Execution (Week 2)

### Current Flow (Sequential)

```
Checkout → Maven Cache → Build → Test → Docker Build → Deploy
Total: 3-5 minutes
```

### Optimized Flow (Parallel)

```
                    ┌─→ Unit Tests (2 min)
Checkout → Build ───┤
                    └─→ Docker Build (2 min)
                              ↓
                         Deploy (1 min)
Total: 3 minutes (33% faster)
```

### Implementation

Update `dev.yml`:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Cache Maven packages
        # ...existing cache setup
      - name: Compile only (no tests)
        run: mvn compile -B
      - name: Upload compiled artifacts
        uses: actions/upload-artifact@v4
        with:
          name: compiled-classes
          path: ./webapp/target/classes
          retention-days: 1

  test:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Download artifacts
        uses: actions/download-artifact@v4
      - name: Run tests only
        run: mvn test -B

  docker-build:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Download artifacts
        uses: actions/download-artifact@v4
      - name: Build Docker image
        # ...Docker build steps

  deploy:
    needs: [ test, docker-build ]
    # ...deployment steps
```

**Savings**: 1-2 minutes per build

---

## 🔥 Phase 3: Maven Daemon (Week 3)

### Problem

Maven starts JVM from scratch every run, wasting 10-20 seconds.

### Solution: Maven Daemon (mvnd)

Update `Dockerfile`:

```dockerfile
FROM maven:3-eclipse-temurin-21 AS build

# Install Maven Daemon
RUN curl -L https://downloads.apache.org/maven/mvnd/1.0-m8/mvnd-1.0-m8-linux-amd64.zip -o mvnd.zip && \
    unzip mvnd.zip && \
    mv mvnd-1.0-m8-linux-amd64 /opt/mvnd && \
    ln -s /opt/mvnd/bin/mvnd /usr/local/bin/mvnd

# Use mvnd instead of mvn
WORKDIR /home/maven/src
COPY pom.xml .
RUN mvnd dependency:go-offline

COPY src ./src/
RUN mvnd clean package -DskipTests -B
```

**Savings**: 15-30 seconds per build (faster JVM startup)

---

## 📦 Phase 4: Artifact Reuse Between Workflows (Week 4)

### Problem

Building twice: once for PR preview, again when merged to master.

### Solution: Reusable Artifacts

Create `build.yml` (triggered on push):

```yaml
name: Build Artifact
on:
  push:
    branches: [ master, 'feature/**' ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      # ...build steps
      - name: Upload Docker image
        uses: actions/upload-artifact@v4
        with:
          name: docker-image-${{ github.sha }}
          path: /tmp/image.tar
          retention-days: 7
```

Update `prod.yml` to download instead of rebuild:

```yaml
jobs:
  deploy:
    steps:
      - name: Download pre-built image
        uses: actions/download-artifact@v4
        with:
          name: docker-image-${{ github.sha }}
      - name: Load image
        run: docker load < /tmp/image.tar
      - name: Deploy to Fly
        # ...deploy steps
```

**Savings**: Eliminates duplicate builds on merge (saves 3-5 minutes)

---

## 🎯 Phase 5: Smart Test Selection (Week 5)

### Problem

Running all tests even when only 1 file changed.

### Solution: Affected Test Detection

Install JUnit Pioneer for tag support:

```xml

<dependency>
    <groupId>org.junit-pioneer</groupId>
    <artifactId>junit-pioneer</artifactId>
    <version>2.2.0</version>
    <scope>test</scope>
</dependency>
```

Tag your tests:

```kotlin
@Tag("unit")
class UserServiceTest { ... }

@Tag("integration")
class DatabaseIntegrationTest { ... }

@Tag("slow")
class EndToEndTest { ... }
```

Update workflow:

```yaml
- name: Detect changed files
  id: changes
  uses: dorny/paths-filter@v2
  with:
    filters: |
      domain:
        - 'src/main/kotlin/app/mcorg/domain/**'
      presentation:
        - 'src/main/kotlin/app/mcorg/presentation/**'
      database:
        - 'src/main/resources/db/migration/**'

- name: Run tests
  run: |
    if [[ "${{ steps.changes.outputs.domain }}" == "true" ]]; then
      mvn test -Dgroups=unit
    elif [[ "${{ steps.changes.outputs.database }}" == "true" ]]; then
      mvn test -Dgroups=integration
    else
      mvn test -Dgroups=unit
    fi
```

**Savings**: 1-3 minutes when only unit tests needed

---

## 🐳 Phase 6: Docker Build Cache Modes (Week 6)

### Problem

GitHub Actions cache has 10GB limit per repo.

### Solution: Use GitHub Container Registry

Update `dev.yml`:

```yaml
- name: Login to GitHub Container Registry
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

- name: Build and push
  uses: docker/build-push-action@v5
  with:
    context: ./webapp
    push: true
    tags: ghcr.io/${{ github.repository }}:${{ github.sha }}
    cache-from: type=registry,ref=ghcr.io/${{ github.repository }}:buildcache
    cache-to: type=registry,ref=ghcr.io/${{ github.repository }}:buildcache,mode=max
```

**Benefits**:

- ✅ Unlimited cache storage
- ✅ Faster cache restoration (registry optimized)
- ✅ Shared cache across workflows

**Savings**: 30-60 seconds (faster cache access)

---

## 🔬 Phase 7: Local Build Optimization (Week 7)

### For Your Local Development

#### 1. Use Maven Wrapper with Daemon

```bash
# Download mvnd
curl -L https://downloads.apache.org/maven/mvnd/1.0-m8/mvnd-1.0-m8-windows-amd64.zip -o mvnd.zip
unzip mvnd.zip -d C:\tools\mvnd

# Add to PATH
$env:PATH += ";C:\tools\mvnd\bin"

# Use mvnd instead of mvn
mvnd clean compile  # 2-3x faster than mvn
```

#### 2. Skip Unnecessary Steps

```bash
# Skip tests when iterating on UI
mvnd compile -DskipTests

# Skip static analysis
mvnd compile -Dcheckstyle.skip -Dspotless.check.skip

# Fast compile only
mvnd compile -T 1C  # Use 1 thread per CPU core
```

#### 3. Use Incremental Compilation

Add to `pom.xml`:

```xml

<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <configuration>
        <incremental>true</incremental>
    </configuration>
</plugin>
```

**Savings**: 50-70% faster local builds

---

## 📊 Phase 8: Build Monitoring (Week 8)

### Track Build Performance Over Time

Add build metrics collection:

```yaml
- name: Report build time
  if: always()
  uses: actions/github-script@v7
  with:
    script: |
      const startTime = new Date('${{ github.event.created_at }}');
      const endTime = new Date();
      const duration = (endTime - startTime) / 1000 / 60; // minutes

      await github.rest.issues.createComment({
        issue_number: context.issue.number,
        owner: context.repo.owner,
        repo: context.repo.repo,
        body: `⏱️ Build completed in ${duration.toFixed(1)} minutes\n\n` +
              `Cache hits:\n` +
              `- Maven: ${{ steps.cache-maven.outputs.cache-hit }}\n` +
              `- Docker: ${{ steps.cache-docker.outputs.cache-hit }}`
      });
```

**Benefits**:

- Track performance trends
- Identify cache misses
- Optimize further based on data

---

## 🎨 Phase 9: Fly.io Machine Optimization (Week 9)

### Current: Remote Builder

```yaml
flyctl deploy --remote-only
```

**Issue**: Remote builder restarts for each deployment.

### Solution: Persistent Build Machine

Create `fly.build.toml`:

```toml
app = "mcorg-builder"
primary_region = "arn"

[build]
builder = "paketobuildpacks/builder:base"

[[services]]
internal_port = 2375
protocol = "tcp"

[[services.ports]]
port = 2375

[[vm]]
size = "shared-cpu-2x"
memory = "2gb"
```

Deploy persistent builder:

```bash
flyctl launch -c fly.build.toml
```

Update workflow:

```yaml
- name: Deploy to Fly
  env:
    DOCKER_HOST: tcp://mcorg-builder.fly.dev:2375
  run: flyctl deploy
```

**Savings**: 30-60 seconds (no builder startup time)

---

## 🧪 Phase 10: Pre-merge Validation (Week 10)

### Add Pre-commit Hooks

Create `.husky/pre-commit`:

```bash
#!/usr/bin/env sh
. "$(dirname -- "$0")/_/husky.sh"

# Quick compile check
cd webapp
mvn compile -DskipTests -B || exit 1

# Kotlin lint
mvn ktlint:check || exit 1

echo "✅ Pre-commit checks passed"
```

Install:

```bash
npm install husky --save-dev
npx husky install
```

**Benefits**:

- Catch compilation errors before CI
- Saves CI minutes on broken builds
- Enforces code style locally

---

## 📈 Combined Impact Projection

| Phase                    | Implementation Time | Build Time Saved    | Cumulative Savings |
|--------------------------|---------------------|---------------------|--------------------|
| **Phase 1** (Current)    | 3 hours             | 10-15 min → 3-5 min | 66% faster         |
| Phase 2 (Parallel)       | 2 hours             | 3-5 min → 2-3 min   | 83% faster         |
| Phase 3 (Maven Daemon)   | 1 hour              | 2-3 min → 2 min     | 87% faster         |
| Phase 4 (Artifacts)      | 2 hours             | Eliminate duplicate | 90% faster         |
| Phase 5 (Smart Tests)    | 3 hours             | 2 min → 1 min       | 93% faster         |
| Phase 6 (Registry Cache) | 1 hour              | 1 min → 0.5 min     | 96% faster         |

**Total Potential**: 15-20 min → **30-60 seconds** (97% faster)

---

## 🎯 Recommended Order

1. **Week 1-2**: Phase 1 (current optimizations)
2. **Week 3**: Verify Phase 1 working, measure actual improvements
3. **Week 4**: Phase 7 (local optimization) - quality of life
4. **Week 5**: Phase 10 (pre-commit hooks) - prevent broken builds
5. **Week 6**: Phase 2 (parallel jobs) - easy big win
6. **Later**: Phases 3-6 only if still needed

---

## ⚠️ When NOT to Optimize Further

Stop optimizing if:

- ✅ Builds consistently < 5 minutes
- ✅ You're not blocked waiting for CI
- ✅ Implementation time > time saved
- ✅ You have bigger priorities (features, users)

**Remember**: The goal is faster iteration, not perfect optimization.

---

## 🔗 Additional Resources

- [Maven Daemon Docs](https://github.com/apache/maven-mvnd)
- [GitHub Actions Best Practices](https://docs.github.com/en/actions/learn-github-actions/best-practices)
- [Docker Build Cloud](https://docs.docker.com/build/cloud/) (alternative to GitHub cache)
- [Fly.io Remote Builders](https://fly.io/docs/reference/builders/)
- [Kotlin Compiler Options](https://kotlinlang.org/docs/compiler-reference.html)

