package com.example.picgram

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ArchitectureBoundaryTest {
    private val sources = File("src/main/java/com/example/picgram").takeIf { it.exists() }
        ?: File("app/src/main/java/com/example/picgram")

    @Test fun viewsNeverCallRemoteOrLocalDataSourcesDirectly() {
        val views = sources.listFiles()!!.filter { it.name.endsWith("Activity.kt") } +
            File(sources, "ui/screens").listFiles()!!.toList()
        val forbidden = Regex("(SupabaseAuth|SupabaseDatabase|SupabaseStorage|SessionManager|MediaFiles|PostOperations)\\.")
        views.forEach { assertFalse("Data source called by ${it.name}", forbidden.containsMatchIn(it.readText())) }
        assertEquals(10, File(sources, "ui/screens").listFiles()!!.count { it.extension == "kt" })
    }

    @Test fun viewModelsNeverOwnViewsContextsOrActivityLifecycles() {
        val models = File(sources, "presentation").listFiles()!!.filter { it.name.endsWith("ViewModel.kt") }
        val forbidden = Regex("import android\\.(app|content|view|widget)\\.|lifecycleScope|setContent\\s*\\{|startActivity\\(|Toast\\.")
        models.forEach { assertFalse("Android UI dependency in ${it.name}", forbidden.containsMatchIn(it.readText())) }
        models.filterNot { it.name == "ScreenViewModel.kt" }.forEach {
            assertTrue("Missing immutable state in ${it.name}", it.readText().contains("asStateFlow()"))
        }
        assertEquals(10, models.count { it.name != "ScreenViewModel.kt" })
    }
}
