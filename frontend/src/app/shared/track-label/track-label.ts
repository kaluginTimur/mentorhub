/** How the UI names a track: its code and its name, joined by a middle dot. */
export const trackLabel = (track: { code: string; name: string }): string =>
  `${track.code} · ${track.name}`;
