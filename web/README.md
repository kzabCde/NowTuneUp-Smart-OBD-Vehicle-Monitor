# NowTuneUp website

Next.js App Router frontend for product documentation and server-side GitHub Release discovery. Configure `.env.local` from `.env.example`, then run `npm ci && npm run dev`. `GITHUB_TOKEN` is optional and server-only; never prefix it with `NEXT_PUBLIC_`. On Vercel, the connected repository is discovered from the system-provided Git metadata when explicit repository variables are not configured.


## Vercel

Import the repository root with the committed root `package.json` and `vercel.json`, or set the Vercel project Root Directory to `web`. Both configurations expose Next.js 15.5.7 during framework detection. The root manifest pins patched React 19.1.2 and React DOM 19.1.2 to the same versions as this workspace so server rendering uses one React runtime. The root workspace is recommended because it works without dashboard-specific directory settings.
