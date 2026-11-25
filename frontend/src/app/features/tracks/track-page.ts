import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  linkedSignal,
  resource,
  signal,
  untracked,
  viewChild,
  viewChildren,
  afterNextRender,
  Injector,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowLeft,
  lucideChevronRight,
  lucidePencil,
  lucidePlus,
  lucideTriangleAlert,
  lucideUserMinus,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api/api';
import { addTrackMentor } from '../../core/api/fn/tracks/add-track-mentor';
import { addTrackCoordinator } from '../../core/api/fn/tracks/add-track-coordinator';
import { archiveTrack } from '../../core/api/fn/tracks/archive-track';
import { getTrack } from '../../core/api/fn/tracks/get-track';
import { getTrackHistory } from '../../core/api/fn/tracks/get-track-history';
import { listTrackCoordinatorCandidates } from '../../core/api/fn/tracks/list-track-coordinator-candidates';
import { listTrackMentorCandidates } from '../../core/api/fn/tracks/list-track-mentor-candidates';
import { removeTrackMentor } from '../../core/api/fn/tracks/remove-track-mentor';
import { removeTrackCoordinator } from '../../core/api/fn/tracks/remove-track-coordinator';
import { restoreTrack } from '../../core/api/fn/tracks/restore-track';
import { updateTrack } from '../../core/api/fn/tracks/update-track';
import { Track } from '../../core/api/models/track';
import { TrackCoordinator } from '../../core/api/models/track-coordinator';
import { TrackMentor } from '../../core/api/models/track-mentor';
import { TrackHistoryEntry } from '../../core/api/models/track-history-entry';
import { TrackUpdate } from '../../core/api/models/track-update';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { PROBLEM, isForbidden, problemOf } from '../../core/problem';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { Loading } from '../../shared/loading/loading';
import { ToastOptions, ToastService } from '../../shared/toast/toast.service';
import { trackLabel } from '../../shared/track-label/track-label';
import { Unavailable } from '../../shared/unavailable/unavailable';
import { TrackEnrollments } from '../enrollments/track-enrollments';
import { AddCoordinatorDialog, AddCoordinatorDialogData } from './add-coordinator-dialog';
import { AddTrackMentorDialog, AddTrackMentorDialogData } from './add-track-mentor-dialog';
import { AssignmentMark } from './assignment-mark';
import { TrackStatusChip } from './track-status-chip';
import { describeTrackEntry } from './track-history-text';
import { TrackDialog, TrackDialogData } from './track-dialog';
import { TracksListState } from './tracks-list-state';
import { Email } from '../../shared/email/email';
import { Avatar } from '../../shared/avatar/avatar';
import { MentorStudents } from './mentor-students';
import { studentsText } from './students-text';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** What became of a change: applied, refused by the server and explained, or failed for another reason. */
type Outcome = 'done' | 'refused' | 'failed';

@Component({
  selector: 'app-track-page',
  imports: [
    Email,
    Avatar,
    MentorStudents,
    DatePipe,
    RouterLink,
    NgIcon,
    HlmButton,
    AssignmentMark,
    TrackStatusChip,
    TrackEnrollments,
    HlmCardImports,
    Loading,
    Unavailable,
  ],
  providers: [
    provideIcons({
      lucideArrowLeft,
      lucideChevronRight,
      lucidePencil,
      lucidePlus,
      lucideTriangleAlert,
      lucideUserMinus,
    }),
  ],
  templateUrl: './track-page.html',
})
export class TrackPage {
  private readonly api = inject(Api);
  private readonly dialog = inject(HlmDialogService);
  private readonly toast = inject(ToastService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly listState = inject(TracksListState);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly trackEnrollments = viewChild(TrackEnrollments);
  private readonly routeParams = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  private readonly id = computed(() => this.routeParams().get('id') ?? '');

  protected readonly track = resource({
    params: () => this.id(),
    loader: async ({ params }): Promise<Track | null> => {
      if (!UUID.test(params)) {
        return null;
      }
      try {
        return await this.api.invoke(getTrack, { id: params });
      } catch (error) {
        if (error instanceof HttpErrorResponse && (error.status === 404 || error.status === 400)) {
          return null;
        }
        throw error;
      }
    },
  });

  protected readonly failed = computed(
    () => !!this.track.error() && !isForbidden(this.track.error()),
  );
  protected readonly canManage = computed(() => this.currentUser.hasPermission('TRACKS_MANAGE'));
  protected readonly canReadEnrollments = computed(() =>
    this.currentUser.hasPermission('ENROLLMENTS_READ'),
  );
  protected readonly canReadUsers = computed(() => this.currentUser.hasPermission('USERS_READ'));
  protected readonly hasMarked = computed(
    () =>
      this.track.hasValue() &&
      (this.track.value()?.coordinators ?? []).some((c) => c.disabled || !c.coordinatorRole),
  );

  protected readonly hasMarkedMentors = computed(
    () =>
      this.track.hasValue() &&
      (this.track.value()?.mentors ?? []).some((m) => m.disabled || !m.mentorRole),
  );

  protected readonly studentsText = studentsText;
  private readonly studentLists = viewChildren(MentorStudents);
  /** Mentors left open; starts empty again on each track, so a list opened on one track does not open on the next. */
  private readonly openMentors = linkedSignal<string, ReadonlySet<string>>({
    source: () => this.id(),
    computation: () => new Set(),
  });
  /** Students can be shown: the user may read enrollments and some mentor leads students. */
  private readonly studentsAvailable = computed(
    () =>
      this.canReadEnrollments() &&
      (this.track.hasValue() ? (this.track.value()?.mentors ?? []) : []).some(
        (m) => m.students > 0,
      ),
  );
  /** The first column of the mentors exists when students can be shown: always on desktop, on a phone only if some mentor leads students. */
  protected readonly mentorColumns = computed(() => {
    if (!this.canReadEnrollments()) {
      return 'grid-cols-[2rem_minmax(0,1fr)_auto] pl-3';
    }
    return this.studentsAvailable()
      ? 'grid-cols-[2.5rem_2rem_minmax(0,1fr)_auto] gap-x-2.5 sm:grid-cols-[3rem_2rem_minmax(0,1fr)_auto] sm:gap-x-3'
      : 'grid-cols-[2rem_minmax(0,1fr)_auto] gap-x-2.5 pl-3 sm:grid-cols-[3rem_2rem_minmax(0,1fr)_auto] sm:gap-x-3 sm:pl-0';
  });
  protected readonly gutterOnPhone = this.studentsAvailable;

  protected isOpen(mentor: TrackMentor): boolean {
    return this.canReadEnrollments() && mentor.students > 0 && this.openMentors().has(mentor.id);
  }

  protected toggle(mentor: TrackMentor): void {
    this.openMentors.update((open) => {
      const next = new Set(open);
      if (!next.delete(mentor.id)) {
        next.add(mentor.id);
      }
      return next;
    });
  }

  /** The bottom chevron: hide the list, then bring the reader back to the mentor's own chevron. */
  protected hideStudents(mentor: TrackMentor): void {
    this.openMentors.update((open) => {
      const next = new Set(open);
      next.delete(mentor.id);
      return next;
    });
    afterNextRender(
      () => {
        const chevron = this.host.nativeElement.querySelector<HTMLElement>(
          `[data-students-toggle="${mentor.id}"]`,
        );
        chevron?.focus();
        chevron?.scrollIntoView?.({ block: 'nearest' });
      },
      { injector: this.injector },
    );
  }

  private reloadStudents(): void {
    for (const list of this.studentLists()) {
      list.reload();
    }
  }

  protected readonly entries = signal<TrackHistoryEntry[]>([]);
  protected readonly historyTotal = signal(0);
  protected readonly historyLoading = signal(false);
  protected readonly historyFailed = signal(false);
  private nextHistoryPage = 0;
  private historyRequest = 0;

  constructor() {
    effect(() => {
      if (isForbidden(this.track.error())) {
        void this.router.navigateByUrl('/');
      }
    });
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.entries.set([]);
        this.historyTotal.set(0);
        if (UUID.test(id)) {
          void this.loadHistory(true);
        }
      });
    });
  }

  protected describe(entry: TrackHistoryEntry): string {
    return describeTrackEntry(entry);
  }

  protected async loadHistory(reset: boolean): Promise<void> {
    const request = ++this.historyRequest;
    const page = reset ? 0 : this.nextHistoryPage;
    this.historyLoading.set(true);
    this.historyFailed.set(false);
    try {
      const result = await this.api.invoke(getTrackHistory, { id: this.id(), page });
      if (request !== this.historyRequest) {
        return;
      }
      this.entries.update((current) => (reset ? result.items : [...current, ...result.items]));
      this.historyTotal.set(result.totalItems);
      this.nextHistoryPage = page + 1;
    } catch {
      if (request === this.historyRequest) {
        this.historyFailed.set(true);
      }
    } finally {
      if (request === this.historyRequest) {
        this.historyLoading.set(false);
      }
    }
  }

  protected edit(track: Track): void {
    const data: TrackDialogData = {
      title: 'Edit track',
      confirmLabel: 'Save',
      initial: { code: track.code, name: track.name },
      save: async (body: TrackUpdate) => {
        try {
          const updated = await this.api.invoke(updateTrack, { id: track.id, body });
          this.applied(updated, 'Track saved');
          return updated;
        } catch (error) {
          if (this.explainRefusal(error)) {
            return null;
          }
          throw error;
        }
      },
    };
    this.dialog.open(TrackDialog, { contentClass: 'sm:max-w-[480px]', context: data });
  }

  protected async archive(track: Track): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Archive ${trackLabel(track)}?`,
        text: 'It becomes read-only until it is restored. Its coordinators and mentors are kept.',
        confirmLabel: 'Archive',
        destructive: true,
      },
    });
    if (await firstValueFrom(ref.closed$)) {
      await this.change(
        () => this.api.invoke(archiveTrack, { id: track.id }),
        'Track archived',
        'Could not archive the track. Try again.',
      );
    }
  }

  protected async restore(track: Track): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Restore ${trackLabel(track)}?`,
        text: 'It becomes editable again. Its coordinators and mentors are kept.',
        confirmLabel: 'Restore',
      },
    });
    if (await firstValueFrom(ref.closed$)) {
      await this.change(
        () => this.api.invoke(restoreTrack, { id: track.id }),
        'Track restored',
        'Could not restore the track. Try again.',
      );
    }
  }

  protected async removeCoordinator(track: Track, coordinator: TrackCoordinator): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Remove ${coordinator.name} as a coordinator?`,
        text: `They stop coordinating ${trackLabel(track)} and can no longer manage its enrollments.`,
        confirmLabel: 'Remove',
        destructive: true,
      },
    });
    if (await firstValueFrom(ref.closed$)) {
      await this.change(
        () => this.api.invoke(removeTrackCoordinator, { id: track.id, userId: coordinator.id }),
        `${coordinator.name} removed`,
        'Could not remove the coordinator. Try again.',
      );
    }
  }

  protected addCoordinator(track: Track): void {
    const data: AddCoordinatorDialogData = {
      trackName: trackLabel(track),
      load: async () => {
        try {
          return (await this.api.invoke(listTrackCoordinatorCandidates, { id: track.id })).items;
        } catch (error) {
          if (this.explainRefusal(error)) {
            return null;
          }
          throw error;
        }
      },
      add: async (userId) =>
        (await this.change(
          () => this.api.invoke(addTrackCoordinator, { id: track.id, userId }),
          'Coordinator added',
          'Could not add the coordinator. Try again.',
        )) !== 'failed',
    };
    this.dialog.open(AddCoordinatorDialog, { contentClass: 'sm:max-w-[480px]', context: data });
  }

  protected async removeMentor(track: Track, mentor: TrackMentor): Promise<void> {
    const ref = this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      contentClass: 'sm:max-w-[420px]',
      context: {
        title: `Remove ${mentor.name} as a mentor?`,
        text: `They stop being a mentor of ${trackLabel(track)} and can no longer be assigned to its enrollments.`,
        confirmLabel: 'Remove',
        destructive: true,
      },
    });
    if (await firstValueFrom(ref.closed$)) {
      if (
        (await this.change(
          () => this.api.invoke(removeTrackMentor, { id: track.id, userId: mentor.id }),
          `${mentor.name} removed`,
          'Could not remove the mentor. Try again.',
        )) === 'done'
      ) {
        this.mentorsChanged();
      }
    }
  }

  protected addMentor(track: Track): void {
    const data: AddTrackMentorDialogData = {
      trackName: trackLabel(track),
      load: async (q, size) => {
        try {
          return await this.api.invoke(listTrackMentorCandidates, { id: track.id, q, size });
        } catch (error) {
          if (this.explainRefusal(error)) {
            return null;
          }
          throw error;
        }
      },
      add: async (userId) => {
        const outcome = await this.change(
          () => this.api.invoke(addTrackMentor, { id: track.id, userId }),
          'Mentor added',
          'Could not add the mentor. Try again.',
        );
        if (outcome === 'done') {
          this.mentorsChanged();
        }
        return outcome !== 'failed';
      },
    };
    this.dialog.open(AddTrackMentorDialog, { contentClass: 'sm:max-w-[520px]', context: data });
  }

  private mentorsChanged(): void {
    this.reloadStudents();
  }

  private async change(
    call: () => Promise<Track>,
    message: string,
    fallback: string,
  ): Promise<Outcome> {
    try {
      this.applied(await call(), message);
      this.heading()?.nativeElement.focus();
      return 'done';
    } catch (error) {
      if (this.explainRefusal(error)) {
        return 'refused';
      }
      if (problemOf(error)?.status !== 403) {
        this.say(fallback);
      }
      return 'failed';
    }
  }

  /** A new snackbar replaces the previous one, so a refusal does not linger beside the next result. */
  private say(message: string, options?: ToastOptions): void {
    this.toast.dismissAll();
    this.toast.show(message, options);
  }

  /** An enrollment was refused elsewhere on the page: show the track and its history as they are now. */
  protected refreshed(): void {
    this.track.reload();
    this.reloadStudents();
    this.trackEnrollments()?.reload();
    void this.loadHistory(true);
  }

  /** An enrollment or its mentors changed: the counts and the open lists change with it (ENR-7.1). */
  protected enrolled(): void {
    this.track.reload();
    this.reloadStudents();
    void this.loadHistory(true);
  }

  private applied(updated: Track, message: string): void {
    this.track.set(updated);
    void this.loadHistory(true);
    this.say(message, { duration: 5000, politeness: 'polite' });
  }

  /** Explains a 409 and shows the track as it is now; false for any other error. */
  private explainRefusal(error: unknown): boolean {
    const type = problemOf(error)?.type;
    if (type === PROBLEM.trackArchived) {
      this.say("This track is archived, so it can't be changed. It is shown as it is now.", {
        politeness: 'assertive',
      });
    } else if (type === PROBLEM.trackHasActiveEnrollments) {
      this.say(
        "This track still has active enrollments, so it can't be archived. It is shown as it is now.",
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.mentorNotEligible) {
      this.say('That user is no longer eligible: they must be active and hold the mentor role.', {
        politeness: 'assertive',
      });
    } else if (type === PROBLEM.mentorLeadsEnrollments) {
      this.say(
        'That mentor still leads active enrollments on this track, so they cannot be removed. The track is shown as it is now.',
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.mentorLeadsCohorts) {
      this.say(
        'That mentor is a mentor of cohorts of this track, so they cannot be removed. Remove them from those cohorts first. The track, its cohorts and its enrollments are shown as they are now.',
        { politeness: 'assertive' },
      );
    } else if (type === PROBLEM.coordinatorNotEligible) {
      this.say(
        'That user is no longer eligible: they must be active and hold the coordinator role.',
        { politeness: 'assertive' },
      );
    } else {
      return false;
    }
    this.refreshed();
    return true;
  }
}
