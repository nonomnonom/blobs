'use client';

import type { ComponentPropsWithoutRef } from 'react';

export function HomeContainer({
    className = '',
    ...props
}: ComponentPropsWithoutRef<'main'>) {
    return (
        <div
            {...props}
            id="nd-home-layout"
            className={`home-shell ${className}`}
        />
    );
}
