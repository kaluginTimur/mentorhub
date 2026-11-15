import { Component, computed, effect, inject, linkedSignal, resource } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucidePlus } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { Api } from '../../core/api/api';
import { createTrack } from '../../core/api/fn/tracks/create-track';
import { listTracks } from '../../core/api/fn/tracks/list-tracks';
import { TrackCreate } from '../../core/api/models/track-create';
import { TrackList } from '../../core/api/models/track-list';
import { TrackStatusFilter } from '../../core/api/models/track-status-filter';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { isForbidden } from '../../core/problem';
import { ToastService } from '../../shared/toast/toast.service';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { AssignmentMark } from './assignment-mark';
import { TRACK_FILTER_LABELS } from './labels';
import { TrackStatusChip } from './track-status-chip';
import { TrackDialog, TrackDialogData } from './track-dialog';
import { TracksListState } from './tracks-list-state';

const FILTERS: TrackStatusFilter[] = ['ACTIVE', 'ARCHIVED', 'ALL'];

@Component({
  selector: 'app-tracks-list',
  imports: [
    RouterLink,
    NgIcon,
    HlmButton,
    HlmLabel,
    HlmSelectImports,
    Unavailable,
    AssignmentMark,
    TrackStatusChip,
  ],
  providers: [provideIcons({ lucidePlus })],
  templateUrl: './tracks-list.html',
})
export class TracksList {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(HlmDialogService);
  private readonly toast = inject(ToastService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly listState = inject(TracksListState);

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly status = computed<TrackStatusFilter>(() => {
    const value = this.params().get('status');
    return FILTERS.find((f) => f === value) ?? 'ACTIVE';
  });

  protected readonly canManage = computed(() => this.currentUser.hasPermission('TRACKS_MANAGE'));

  protected readonly tracks = resource({
    params: () => ({ status: this.status() }),
    loader: ({ params }) => this.api.invoke(listTracks, { status: params.status }),
  });
  /** A failed load that is not a refusal; a refusal sends the user home. */
  protected readonly failed = computed(
    () => !!this.tracks.error() && !isForbidden(this.tracks.error()),
  );
  /** The last loaded list: it stays on screen while the next one loads. */
  protected readonly loaded = linkedSignal<TrackList | undefined, TrackList | undefined>({
    source: () => (this.tracks.hasValue() ? this.tracks.value() : undefined),
    computation: (list, previous) => list ?? previous?.value,
  });

  protected readonly filters = FILTERS;
  protected readonly filterLabels = TRACK_FILTER_LABELS;
  protected readonly filterLabel = (value: TrackStatusFilter): string =>
    TRACK_FILTER_LABELS[value] ?? '';

  constructor() {
    effect(() => {
      if (isForbidden(this.tracks.error())) {
        void this.router.navigateByUrl('/');
      }
    });
    effect(() => {
      const query: Params = {};
      for (const key of this.params().keys) {
        query[key] = this.params().get(key);
      }
      this.listState.query.set(query);
    });
  }

  protected setStatus(value: TrackStatusFilter | null | undefined): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: value && value !== 'ACTIVE' ? value : null },
      queryParamsHandling: 'merge',
    });
  }

  protected create(): void {
    const data: TrackDialogData = {
      title: 'Create track',
      confirmLabel: 'Create',
      initial: { code: '', name: '' },
      save: async (body: TrackCreate) => {
        const created = await this.api.invoke(createTrack, { body });
        this.toast.dismissAll();
        this.toast.show('Track created', { duration: 5000, politeness: 'polite' });
        this.tracks.reload();
        return created;
      },
    };
    this.dialog.open(TrackDialog, { contentClass: 'sm:max-w-[480px]', context: data });
  }
}
