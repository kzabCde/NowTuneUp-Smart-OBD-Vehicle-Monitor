# NowTuneUp website

Next.js App Router frontend for product documentation and server-side GitHub Release discovery. Configure `.env.local` from `.env.example`, then run `npm ci && npm run dev`. `GITHUB_TOKEN` is optional and server-only; never prefix it with `NEXT_PUBLIC_`.


## Vercel

Import the repository root with the committed root `package.json` and `vercel.json`, or set the Vercel project Root Directory to `web`. Both configurations expose Next.js 15.3.3 during framework detection. The root workspace is recommended because it works without dashboard-specific directory settings.
