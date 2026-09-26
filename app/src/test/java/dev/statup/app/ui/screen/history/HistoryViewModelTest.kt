package dev.statup.app.ui.screen.history

import dev.statup.app.domain.model.Transaction
import dev.statup.app.domain.model.TransactionSource
import dev.statup.app.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    /**
     * Regression test for a lost-update race: a filter change made mid-aggregation must not be
     * reverted when the aggregation writes its result back (the buggy version overwrote it).
     */
    @Test
    fun `filter change during aggregation is not clobbered by the write-back`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val source = MutableSharedFlow<List<Transaction>>(extraBufferCapacity = 8)
            // Compute dispatcher is paused (Standard) so we can interleave a user action
            // between the emission and the aggregation's write-back.
            val vm = HistoryViewModel(
                transactions = source,
                playerStats = flowOf(null),
                computeDispatcher = StandardTestDispatcher(testScheduler)
            )

            // New transactions arrive; the collector starts aggregating and parks at the
            // compute dispatcher (not yet advanced).
            source.tryEmit(
                listOf(
                    Transaction(type = TransactionType.EARN, source = TransactionSource.MANUAL, points = 5),
                    Transaction(type = TransactionType.REDEEM, source = TransactionSource.REWARD, points = 3),
                )
            )

            // User taps a filter mid-aggregation.
            vm.setFilter(HistoryFilter.EARNED)
            assertEquals(HistoryFilter.EARNED, vm.uiState.value.selectedFilter)

            // Aggregation finishes and writes back.
            advanceUntilIdle()

            assertEquals(HistoryFilter.EARNED, vm.uiState.value.selectedFilter)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
