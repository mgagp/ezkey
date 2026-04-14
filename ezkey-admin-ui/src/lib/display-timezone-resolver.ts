/**
 * Module-level resolver so date formatters (non-React) can use the active display IANA zone.
 * {@link DisplayTimezoneProvider} updates this when preferences or tenant data change.
 */

type Resolver = () => string | undefined;

let resolver: Resolver = () => undefined;

export function setDisplayTimeZoneResolver(next: Resolver): void {
  resolver = next;
}

/** Returns the active IANA zone for {@link Intl.DateTimeFormat}, or undefined for browser local. */
export function getDisplayTimeZoneId(): string | undefined {
  return resolver();
}
