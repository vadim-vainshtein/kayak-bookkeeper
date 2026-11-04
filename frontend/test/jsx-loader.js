import { readFile } from 'node:fs/promises';
import { transformWithOxc } from 'vite';

export async function load(url, context, nextLoad) {
    if (!url.endsWith('.jsx')) return nextLoad(url, context);
    const source = await readFile(new URL(url), 'utf8');
    const result = await transformWithOxc(source, url, { jsx: { runtime: 'automatic' } });
    return { format: 'module', source: result.code, shortCircuit: true };
}
