import { writable } from 'svelte/store';

let dotInterval: ReturnType<typeof setInterval> | null = null;
export const dotCount = writable(0);
export const dotsToShow = writable('');

// Mettre à jour dotsToShow quand dotCount change
dotCount.subscribe(count => {
  dotsToShow.set('.'.repeat(count));
});

export function startDotAnimation() {
  if (dotInterval === null) {
    dotInterval = setInterval(() => {
      dotCount.update(count => (count + 1) % 4);
    }, 500);
  }
}

export function stopDotAnimation() {
  if (dotInterval !== null) {
    clearInterval(dotInterval);
    dotInterval = null;
  }
  dotCount.set(0);
}