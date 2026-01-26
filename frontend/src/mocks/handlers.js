import { rest } from 'msw';

let people = [];

export const handlers = [
    rest.get('/api/people', (_req, res, ctx) => {
        return res(ctx.status(200), ctx.json(people));
    }),

    rest.post('/api/people', async (req, res, ctx) => {
        // Your component sends SAMPLE_PEOPLE (an array)
        const body = await req.json();
        people = Array.isArray(body) ? body : (body?.people ?? []);
        return res(ctx.status(200), ctx.json({ ok: true }));
    }),
];

export function seedPeople(nextPeople) {
    people = Array.isArray(nextPeople) ? nextPeople : [];
}
