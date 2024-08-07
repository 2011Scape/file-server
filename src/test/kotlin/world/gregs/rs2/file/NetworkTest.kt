package world.gregs.rs2.file

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

@ExperimentalCoroutinesApi
internal class NetworkTest {
    @Test
    fun `Connect, sync, ack and fulfill`() =
        runTest {
            mockkStatic("world.gregs.rs2.file.JagexTypesKt")
            mockkStatic("io.ktor.network.sockets.SocketsKt")
            val network = spyk(Network(mockk(), intArrayOf(), 0))
            val socket: Socket = mockk()
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            every { socket.openReadChannel() } returns read
            every { socket.openWriteChannel(any()) } returns write

            coEvery { network.synchronise(read, write) } just Runs
            coEvery { network.acknowledge(read, write) } returns true
            coEvery { network.readRequests(read, write) } just Runs

            network.connect(socket)

            coVerifyOrder {
                network.synchronise(read, write)
                network.acknowledge(read, write)
                network.readRequests(read, write)
            }
        }

    @Test
    fun `Failed ack won't fulfill`() =
        runTest {
            mockkStatic("world.gregs.rs2.file.JagexTypesKt")
            mockkStatic("io.ktor.network.sockets.SocketsKt")
            val network = spyk(Network(mockk(), intArrayOf(), 0))
            val socket: Socket = mockk()
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            every { socket.openReadChannel() } returns read
            every { socket.openWriteChannel(any()) } returns write

            coEvery { network.synchronise(read, write) } just Runs
            coEvery { network.acknowledge(read, write) } returns false
            coEvery { network.readRequests(read, write) } just Runs

            network.connect(socket)

            coVerifyOrder {
                network.synchronise(read, write)
                network.acknowledge(read, write)
            }
            coVerify(exactly = 0) {
                network.readRequests(read, write)
            }
        }

    @Test
    fun `Synchronise client and server`() =
        runTest {
            val revision = 1337
            val keys = intArrayOf(0xffff, 0xfff, 0xff, 0xf)
            val network = Network(mockk(), keys, revision)
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            coEvery { write.writeByte(any()) } just Runs
            coEvery { write.writeInt(any()) } just Runs

            coEvery { read.readByte() } returns 15
            coEvery { read.readInt() } returns revision

            network.synchronise(read, write)

            coVerifyOrder {
                write.writeByte(0)
                write.writeInt(0xffff)
                write.writeInt(0xfff)
                write.writeInt(0xff)
                write.writeInt(0xf)
            }
        }

    @Test
    fun `Fail to synchronise with wrong revision`() =
        runTest {
            val revision = 10
            val network = Network(mockk(), intArrayOf(), revision)
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            coEvery { write.writeByte(any()) } just Runs
            coEvery { write.close() } returns true

            coEvery { read.readByte() } returns 15
            coEvery { read.readInt() } returns 13

            network.synchronise(read, write)

            coVerifyOrder {
                write.writeByte(6)
                write.close()
            }
            coVerify(exactly = 0) {
                write.writeByte(0)
            }
        }

    @Test
    fun `Fail to synchronise with wrong id`() =
        runTest {
            val revision = 420
            val network = Network(mockk(), intArrayOf(), revision)
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            coEvery { write.writeByte(any()) } just Runs
            coEvery { write.close() } returns true

            coEvery { read.readByte() } returns 123

            network.synchronise(read, write)

            coVerifyOrder {
                write.writeByte(11)
                write.close()
                read.readInt() wasNot Called
            }
        }

    @Test
    fun `Acknowledge client`() =
        runTest {
            mockkStatic("world.gregs.rs2.file.JagexTypesKt")
            val network = Network(mockk(), intArrayOf(), 0)
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            coEvery { read.readByte() } returns 6
            coEvery { read.readMedium() } returns 3

            val acknowledged = network.acknowledge(read, write)

            assertTrue(acknowledged)
        }

    @Test
    fun `Don't acknowledge wrong opcode`() =
        runTest {
            val network = Network(mockk(), intArrayOf(), 0)
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            coEvery { write.writeByte(any()) } just Runs
            coEvery { write.close() } returns true

            coEvery { read.readByte() } returns 5

            val acknowledged = network.acknowledge(read, write)

            assertFalse(acknowledged)
            coVerifyOrder {
                write.writeByte(11)
                write.close()
            }
        }

    @Test
    fun `Don't acknowledge wrong session id`() =
        runTest {
            mockkStatic("world.gregs.rs2.file.JagexTypesKt")
            val network = Network(mockk(), intArrayOf(), 0)
            val read: ByteReadChannel = mockk()
            val write: ByteWriteChannel = mockk()

            coEvery { write.writeByte(any()) } just Runs
            coEvery { write.close() } returns true

            coEvery { read.readByte() } returns 6
            coEvery { read.readMedium() } returns 12

            val acknowledged = network.acknowledge(read, write)

            assertFalse(acknowledged)
            coVerifyOrder {
                write.writeByte(10)
                write.close()
            }
        }

    @TestFactory
    fun `Verify status update`() =
        intArrayOf(3, 2).map { opcode ->
            mockkStatic("world.gregs.rs2.file.JagexTypesKt")
            dynamicTest("Verify status logged ${if (opcode == 3) "out" else "in"}") {
                runTest {
                    val network = Network(mockk(), intArrayOf(), 0)
                    val read: ByteReadChannel = mockk()
                    val write: ByteWriteChannel = mockk()

                    coEvery { write.writeByte(any()) } just Runs

                    coEvery { read.readByte() } returns opcode.toByte()
                    coEvery { read.readMedium() } returns 0

                    network.readRequest(read, write)

                    coVerify(exactly = 0) {
                        write.writeByte(10)
                        write.close()
                    }
                }
            }
        }

    @TestFactory
    fun `Invalid status update session id`() =
        intArrayOf(3, 2).map { opcode ->
            mockkStatic("world.gregs.rs2.file.JagexTypesKt")
            dynamicTest("Invalid status logged ${if (opcode == 3) "out" else "in"}") {
                runTest {
                    val network = Network(mockk(), intArrayOf(), 0)
                    val read: ByteReadChannel = mockk()
                    val write: ByteWriteChannel = mockk()

                    coEvery { write.writeByte(any()) } just Runs
                    coEvery { write.close() } returns true

                    coEvery { read.readByte() } returns opcode.toByte()
                    coEvery { read.readMedium() } returns 123

                    network.readRequest(read, write)

                    coVerifyOrder {
                        write.writeByte(10)
                        write.close()
                    }
                }
            }
        }
}
