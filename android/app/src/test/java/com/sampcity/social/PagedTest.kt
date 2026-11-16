package com.sampcity.social

import com.sampcity.social.ui.components.Paged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagedTest {
    private fun page(from: Int, n: Int) = (from until from + n).toList()

    @Test fun cargaPorPaginasYTermina() = runTest(UnconfinedTestDispatcher()) {
        val p = Paged<Int>(this, { it }, { it.size > 12 }, { it.take(12) }) { last, pg -> if (pg == 0) page(1, 13) else page(last!! + 1, 5) }
        p.refresh(true)
        assertEquals(12, p.items.size); assertFalse(p.end); assertTrue(p.loaded)
        p.more(); assertEquals(17, p.items.size); assertTrue(p.end)       // la segunda página trae 5: no hay más
        p.more(); assertEquals(17, p.items.size)                           // al final no vuelve a pedir
    }

    @Test fun noRepiteElementos() = runTest(UnconfinedTestDispatcher()) {
        val p = Paged<Int>(this, { it }, { it.size > 3 }, { it.take(3) }) { _, pg -> if (pg == 0) listOf(1, 2, 3, 4) else listOf(3, 4, 5, 6, 7) }
        p.refresh(true); p.more()
        assertEquals(listOf(1, 2, 3, 4, 5), p.items.toList())              // cada página se recorta a 3 y el 3 repetido no se agrega
    }

    @Test fun errorSeMuestraYSeReintenta() = runTest(UnconfinedTestDispatcher()) {
        var fail = true
        val p = Paged<Int>(this, { it }, { false }) { _, _ -> if (fail) throw com.sampcity.social.data.ApiError(0, "Sin conexión") else listOf(1, 2) }
        p.refresh(true); assertEquals("Sin conexión", p.error); assertFalse(p.loaded); assertTrue(p.items.isEmpty())
        fail = false; p.refresh(true); assertEquals(null, p.error); assertEquals(2, p.items.size)
    }

    @Test fun actualizarYQuitar() = runTest(UnconfinedTestDispatcher()) {
        val p = Paged<Int>(this, { it }, { false }) { _, _ -> listOf(1, 2, 3) }
        p.refresh(true); p.update({ it == 2 }, 20); p.remove { it == 3 }
        assertEquals(listOf(1, 20), p.items.toList()); assertNotNull(p)
    }
}
