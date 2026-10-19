'use client';

import { useStaticSearch } from 'fumadocs-core/search/client';
import { basePath } from '@/lib/base-path';
import {
    SearchDialog,
    SearchDialogClose,
    SearchDialogContent,
    SearchDialogHeader,
    SearchDialogIcon,
    SearchDialogInput,
    SearchDialogList,
    SearchDialogOverlay,
    type SharedProps,
} from 'fumadocs-ui/components/dialog/search';

export default function Search(props: SharedProps) {
    const search = useStaticSearch({ from: `${basePath}/api/search` });

    return (
        <SearchDialog {...search} {...props}>
            <SearchDialogOverlay />
            <SearchDialogContent>
                <SearchDialogHeader>
                    <SearchDialogIcon />
                    <SearchDialogInput placeholder="Find a guide…" />
                    <SearchDialogClose />
                </SearchDialogHeader>
                <SearchDialogList />
            </SearchDialogContent>
        </SearchDialog>
    );
}
