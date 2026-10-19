'use client';

import { useTheme } from 'next-themes';
import { useSyncExternalStore, type ComponentPropsWithoutRef } from 'react';

const subscribe = () => () => {};

export function Appearance({
    className = '',
}: ComponentPropsWithoutRef<'div'>) {
    const { theme, setTheme } = useTheme();
    const mounted = useSyncExternalStore(
        subscribe,
        () => true,
        () => false,
    );

    return (
        <div className={`appearance ${className}`}>
            <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.7"
                aria-hidden="true"
            >
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4M17.6 6.4 19 5" />
            </svg>
            <select
                aria-label="Appearance"
                value={mounted ? (theme ?? 'system') : 'system'}
                onChange={(event) => setTheme(event.target.value)}
            >
                <option value="light">Light</option>
                <option value="dark">Dark</option>
                <option value="system">System</option>
            </select>
        </div>
    );
}
