/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.showdetails.details

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.os.bundleOf
import app.tivi.SharedElementHelper
import app.tivi.data.entities.ActionDate
import app.tivi.data.entities.Episode
import app.tivi.data.entities.Season
import app.tivi.data.entities.TiviShow
import app.tivi.extensions.doOnApplyWindowInsets
import app.tivi.extensions.updateConstraintSets
import app.tivi.showdetails.ShowDetailsNavigator
import app.tivi.ui.TransitionListenerAdapter
import app.tivi.TiviMvRxFragment
import app.tivi.showdetails.details.databinding.FragmentShowDetailsBinding
import com.airbnb.mvrx.MvRx
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import kotlinx.android.parcel.Parcelize
import javax.inject.Inject

class ShowDetailsFragment : TiviMvRxFragment() {
    companion object {
        @JvmStatic
        fun create(id: Long): ShowDetailsFragment {
            return ShowDetailsFragment().apply {
                arguments = bundleOf(MvRx.KEY_ARG to Arguments(id))
            }
        }
    }

    @Parcelize
    data class Arguments(val showId: Long) : Parcelable

    private val viewModel: ShowDetailsFragmentViewModel by fragmentViewModel()
    @Inject lateinit var showDetailsViewModelFactory: ShowDetailsFragmentViewModel.Factory

    @Inject lateinit var controller: ShowDetailsEpoxyController
    @Inject lateinit var showDetailsNavigator: ShowDetailsNavigator
    @Inject lateinit var textCreator: ShowDetailsTextCreator

    private lateinit var binding: FragmentShowDetailsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentShowDetailsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textCreator = textCreator

        binding.detailsMotion.doOnApplyWindowInsets { v, insets, _ ->
            (v as MotionLayout).updateConstraintSets {
                constrainHeight(R.id.details_status_bar_anchor, insets.systemWindowInsetTop)
            }
        }

        // Make the MotionLayout draw behind the status bar
        binding.detailsMotion.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding.detailsMotion.setTransitionListener(object : TransitionListenerAdapter() {
            override fun onTransitionCompleted(parent: MotionLayout, currentId: Int) {
                super.onTransitionCompleted(parent, currentId)

                when (currentId) {
                    R.id.show_details_open -> {
                        if (!binding.detailsFollowFab.isOrWillBeShown) {
                            binding.detailsFollowFab.show()
                        }
                    }
                    R.id.show_details_closed -> {
                        if (!binding.detailsFollowFab.isOrWillBeHidden) {
                            binding.detailsFollowFab.hide()
                        }
                    }
                }
            }

            override fun onTransitionChange(
                parent: MotionLayout,
                startId: Int,
                endId: Int,
                progress: Float,
                positive: Boolean
            ) {
                if (startId == R.id.show_details_open && endId == R.id.show_details_closed) {
                    if (progress >= 0.53 && positive &&
                            !binding.detailsFollowFab.isOrWillBeHidden) {
                        binding.detailsFollowFab.hide()
                    } else if (progress <= 0.47 && !positive &&
                            !binding.detailsFollowFab.isOrWillBeShown) {
                        binding.detailsFollowFab.show()
                    }
                }
            }
        })

        binding.detailsFollowFab.setOnClickListener {
            viewModel.onToggleMyShowsButtonClicked()
        }

        binding.detailsToolbar.setNavigationOnClickListener {
            viewModel.onUpClicked(showDetailsNavigator)
        }

        binding.detailsToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_refresh -> {
                    viewModel.refresh(true)
                    true
                }
                else -> false
            }
        }

        controller.callbacks = object : ShowDetailsEpoxyController.Callbacks {
            override fun onRelatedShowClicked(show: TiviShow, view: View) {
                viewModel.onRelatedShowClicked(
                        showDetailsNavigator,
                        show,
                        SharedElementHelper().apply {
                            addSharedElement(view, "poster")
                        }
                )
            }

            override fun onEpisodeClicked(episode: Episode, view: View) {
                viewModel.onRelatedShowClicked(showDetailsNavigator, episode)
            }

            override fun onMarkSeasonUnwatched(season: Season) {
                viewModel.onMarkSeasonUnwatched(season)
            }

            override fun onMarkSeasonWatched(season: Season, onlyAired: Boolean, date: ActionDate) {
                viewModel.onMarkSeasonWatched(season, onlyAired, date)
            }

            override fun toggleSeasonExpanded(season: Season) {
                viewModel.toggleSeasonExpanded(season)
            }

            override fun onMarkSeasonFollowed(season: Season) {
                viewModel.onMarkSeasonFollowed(season)
            }

            override fun onMarkSeasonIgnored(season: Season) {
                viewModel.onMarkSeasonIgnored(season)
            }

            override fun onMarkPreviousSeasonsIgnored(season: Season) {
                viewModel.onMarkPreviousSeasonsIgnored(season)
            }
        }

        binding.detailsRv.setController(controller)
    }

    override fun invalidate() {
        withState(viewModel) {
            if (binding.state == null) {
                // First time we've had state, start any postponed transitions
                scheduleStartPostponedTransitions()
            }

            binding.state = it
            controller.setData(it)
        }
    }
}