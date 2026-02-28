package app.mcorg.domain.model.minecraft

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MinecraftVersionTest {

    @Nested
    inner class VersionTests {
        @Test
        fun `test release version toString`() {
            val release = MinecraftVersion.release(20, 4)
            assertEquals("1.20.4", release.toString())
        }

        @Test
        fun `test snapshot version toString`() {
            val snapshot = MinecraftVersion.snapshot(2023, 45, 'a')
            assertEquals("23w45a", snapshot.toString())
        }

        @Test
        fun `test snapshot with forRelease toString`() {
            val release = MinecraftVersion.release(20, 0)
            val snapshot = MinecraftVersion.snapshot(2023, 45, 'a', release)
            assertEquals("23w45a", snapshot.toString()) // Should not include forRelease in string output
        }

        @Test
        fun `test compare two releases`() {
            val v1 = MinecraftVersion.release(19, 2)
            val v2 = MinecraftVersion.release(20, 0)
            val v3 = MinecraftVersion.release(20, 0)
            val v4 = MinecraftVersion.release(20, 1)

            assertTrue(v1 < v2)
            assertEquals(0, (v2 as MinecraftVersion).compareTo(v3))
            assertTrue(v3 < v4)
        }

        @Test
        fun `test compare two snapshots`() {
            val s1 = MinecraftVersion.snapshot(2022, 40, 'a')
            val s2 = MinecraftVersion.snapshot(2023, 10, 'b')
            val s3 = MinecraftVersion.snapshot(2023, 10, 'b')
            val s4 = MinecraftVersion.snapshot(2023, 10, 'c')
            val s5 = MinecraftVersion.snapshot(2023, 11, 'a')

            assertTrue(s1 < s2)
            assertEquals(0, (s2 as MinecraftVersion).compareTo(s3))
            assertTrue(s3 < s4)
            assertTrue(s4 < s5)
        }

        @Test
        fun `test compare snapshots with different case patches`() {
            val s1 = MinecraftVersion.snapshot(2023, 10, 'a')
            val s2 = MinecraftVersion.snapshot(2023, 10, 'A')

            assertEquals(0, (s1 as MinecraftVersion).compareTo(s2)) // Case shouldn't matter
        }

        @Test
        fun `test snapshot vs release without forRelease`() {
            val snapshot = MinecraftVersion.snapshot(2023, 45, 'a')
            val release = MinecraftVersion.release(20, 0)

            assertTrue(snapshot < release)
            assertTrue(release > snapshot)
        }

        @Test
        fun `test snapshot vs release with forRelease`() {
            val release = MinecraftVersion.release(20, 0)
            val olderRelease = MinecraftVersion.release(19, 4)

            val snapshotForCurrent = MinecraftVersion.snapshot(2023, 45, 'a', release)
            val snapshotForOlder = MinecraftVersion.snapshot(2023, 40, 'b', olderRelease)

            // Compare snapshots with their targeted releases
            assertTrue(snapshotForOlder < release)
            assertEquals(0, (snapshotForCurrent as MinecraftVersion).compareTo(release))

            // Compare snapshots for different releases
            assertTrue(snapshotForOlder < snapshotForCurrent)
        }

        @Test
        fun `test snapshots for different releases are properly ordered`() {
            val release1 = MinecraftVersion.release(19, 0)
            val release2 = MinecraftVersion.release(20, 0)

            val earlySnapshot = MinecraftVersion.snapshot(2022, 10, 'a', release1)
            val laterSnapshot = MinecraftVersion.snapshot(2023, 45, 'a', release2)

            assertTrue(earlySnapshot < laterSnapshot)
            assertTrue(laterSnapshot > earlySnapshot)
        }
    }

    @Nested
    inner class FromStringTests {
        @Test
        fun `test parse release version`() {
            val version = MinecraftVersion.fromString("1.20.4")
            assertTrue(version is MinecraftVersion.Release)
            val release = version as MinecraftVersion.Release
            assertEquals(1, release.major)
            assertEquals(20, release.minor)
            assertEquals(4, release.patch)
            assertEquals("1.20.4", release.toString())
        }

        @Test
        fun `test parse snapshot version`() {
            val version = MinecraftVersion.fromString("23w45a")
            assertTrue(version is MinecraftVersion.Snapshot)
            val snapshot = version as MinecraftVersion.Snapshot
            assertEquals(2023, snapshot.year)
            assertEquals(45, snapshot.week)
            assertEquals('a', snapshot.patch)
            assertEquals("23w45a", snapshot.toString())
        }

        @Test
        fun `test parse snapshot with uppercase patch`() {
            val version = MinecraftVersion.fromString("23w45A")
            assertTrue(version is MinecraftVersion.Snapshot)
            val snapshot = version as MinecraftVersion.Snapshot
            assertEquals('a', snapshot.patch) // Should be normalized to lowercase
        }

        @Test
        fun `test parse various release versions`() {
            val versions = listOf("1.19.2", "1.20.0", "2.0.1", "1.18", "26.1", "26.1.1")
            versions.forEach { versionString ->
                val version = MinecraftVersion.fromString(versionString)
                assertTrue(version is MinecraftVersion.Release)
                if (versionString.count { it == '.' } == 1) {
                    assertEquals("$versionString.0", version.toString())
                } else {
                    assertEquals(versionString, version.toString())
                }
            }
        }

        @Test
        fun `test parse various snapshot versions`() {
            val versions = mapOf(
                "22w40a" to Triple(2022, 40, 'a'),
                "23w01b" to Triple(2023, 1, 'b'),
                "24w52z" to Triple(2024, 52, 'z')
            )

            versions.forEach { (versionString, expected) ->
                val version = MinecraftVersion.fromString(versionString)
                assertTrue(version is MinecraftVersion.Snapshot)
                val snapshot = version as MinecraftVersion.Snapshot
                assertEquals(expected.first, snapshot.year)
                assertEquals(expected.second, snapshot.week)
                assertEquals(expected.third, snapshot.patch)
            }
        }

        @Test
        fun `test parse invalid version formats throws exception`() {
            val invalidVersions = listOf(
                "1.20.4.1", // Too many parts
                "2023w45", // Missing patch letter
                "w45a", // Missing year
                "2023w", // Missing week and patch
                "invalid", // Completely invalid
                "", // Empty string
                "1.20.a", // Non-numeric patch
                "23wa5a" // Non-numeric week
            )

            invalidVersions.forEach { invalidVersion ->
                assertThrows(IllegalArgumentException::class.java) {
                    MinecraftVersion.fromString(invalidVersion)
                }
            }
        }

        @Test
        fun `test roundtrip parsing`() {
            val originalVersions = listOf(
                MinecraftVersion.release(20, 4),
                MinecraftVersion.snapshot(2023, 45, 'a'),
                MinecraftVersion.release(19, 2),
                MinecraftVersion.snapshot(2022, 1, 'z')
            )

            originalVersions.forEach { original ->
                val versionString = original.toString()
                val parsed = MinecraftVersion.fromString(versionString)
                assertEquals(0, original.compareTo(parsed))
                assertEquals(original.toString(), parsed.toString())
            }
        }
    }

    @Nested
    inner class RangeTests {
        @Test
        fun `test unbounded range`() {
            val range = MinecraftVersionRange.Unbounded
            val version = MinecraftVersion.release(20, 0)

            assertTrue(range.contains(version))
        }

        @Test
        fun `test bounded range`() {
            val from = MinecraftVersion.release(18, 0)
            val to = MinecraftVersion.release(20, 0)
            val range = MinecraftVersionRange.Bounded(from, to)

            val v1 = MinecraftVersion.release(17, 0)
            val v2 = MinecraftVersion.release(18, 0)
            val v3 = MinecraftVersion.release(19, 0)
            val v4 = MinecraftVersion.release(20, 0)
            val v5 = MinecraftVersion.release(20, 1)

            assertFalse(range.contains(v1))
            assertTrue(range.contains(v2))
            assertTrue(range.contains(v3))
            assertTrue(range.contains(v4))
            assertFalse(range.contains(v5))
        }

        @Test
        fun `test upper bounded range`() {
            val to = MinecraftVersion.release(19, 0)
            val range = MinecraftVersionRange.UpperBounded(to)

            val v1 = MinecraftVersion.release(18, 0)
            val v2 = MinecraftVersion.release(19, 0)
            val v3 = MinecraftVersion.release(19, 1)

            assertTrue(range.contains(v1))
            assertTrue(range.contains(v2))
            assertFalse(range.contains(v3))
        }

        @Test
        fun `test upper bounded range with snapshots`() {
            val release = MinecraftVersion.release(20, 0)
            val snapshot = MinecraftVersion.snapshot(2023, 45, 'a', release)
            val upperBoundedRange = MinecraftVersionRange.UpperBounded(release)

            assertTrue(upperBoundedRange.contains(snapshot))

            val laterSnapshot = MinecraftVersion.snapshot(2023, 46, 'a', MinecraftVersion.release(20, 1))
            assertFalse(upperBoundedRange.contains(laterSnapshot))
        }

        @Test
        fun `test lower bounded range`() {
            val from = MinecraftVersion.release(19, 0)
            val range = MinecraftVersionRange.LowerBounded(from)

            val v1 = MinecraftVersion.release(18, 0)
            val v2 = MinecraftVersion.release(19, 0)
            val v3 = MinecraftVersion.release(19, 1)

            assertFalse(range.contains(v1))
            assertTrue(range.contains(v2))
            assertTrue(range.contains(v3))
        }

        @Test
        fun `test ranges with snapshots`() {
            val release1 = MinecraftVersion.release(19, 0)
            val release2 = MinecraftVersion.release(20, 0)
            val snapshot = MinecraftVersion.snapshot(2023, 45, 'a', release2)

            val boundedRange = MinecraftVersionRange.Bounded(release1, release2)

            assertTrue(boundedRange.contains(snapshot))

            val lowerBoundedRange = MinecraftVersionRange.LowerBounded(release2)
            assertTrue(lowerBoundedRange.contains(snapshot))
        }
    }
}