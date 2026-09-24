package com.nendo.argosy.data.remote.romm

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.Response

private const val SERVER_URL = "http://romm.local:8080/assets/romm/resources/manual.pdf"
private const val FOREIGN_URL = "https://files.example.com/manual.pdf"

/**
 * A resource fetch rides on the RomM session, so it may only go to the connected server.
 */
class RomMOpenResourceTest {

    private val body = mockk<ResponseBody>()
    private val api = mockk<RomMApi> {
        coEvery { downloadRaw(any()) } returns Response.success(body)
    }
    private val apiClient = mockk<RomMApiClient> {
        every { this@mockk.api } returns this@RomMOpenResourceTest.api
        every { isSameRommHost(SERVER_URL) } returns true
        every { isSameRommHost(FOREIGN_URL) } returns false
    }
    private val repository = RomMRepository(
        connectionManager = mockk(relaxed = true),
        apiClient = apiClient,
        librarySyncService = mockk(relaxed = true),
        collectionSyncService = mockk(relaxed = true),
        userPropertyService = mockk(relaxed = true),
        achievementService = mockk(relaxed = true),
        userPreferencesRepository = mockk(relaxed = true)
    )

    @Test
    fun `a resource on the connected server is fetched`() = runTest {
        assertSame(body, repository.openResource(SERVER_URL))
    }

    @Test
    fun `a resource on another host is never requested with the session`() = runTest {
        assertNull(repository.openResource(FOREIGN_URL))
        verify(exactly = 0) { apiClient.api }
    }
}
