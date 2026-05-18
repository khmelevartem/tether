package com.tubetoast.tether.config

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tubetoast.tether.TetherApp
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TetherApp::class)
class DeviceNamePersistenceAndroidTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun makePersistence(): DeviceNamePersistenceAndroid {
        val dataStore = PreferenceDataStoreFactory.create {
            tmpFolder.newFile("device_name_${System.nanoTime()}.preferences_pb")
        }
        return DeviceNamePersistenceAndroid(context, dataStore)
    }

    @Test
    fun `read returns null on empty store`() = runTest {
        assertNull(makePersistence().read())
    }

    @Test
    fun `write then read round-trips value`() = runTest {
        val persistence = makePersistence()
        persistence.write("MyPhone")
        assertEquals("MyPhone", persistence.read())
    }

    @Test
    fun `write overwrites previous value`() = runTest {
        val persistence = makePersistence()
        persistence.write("First")
        persistence.write("Second")
        assertEquals("Second", persistence.read())
    }
}
