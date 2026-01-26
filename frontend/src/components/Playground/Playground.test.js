import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { seedPeople } from '../../mocks/handlers';

import Playground from './Playground';

const realFetch = global.fetch;

afterEach(() => {
    global.fetch = realFetch;
    jest.clearAllMocks();
});

test('loads Playground', async () => {
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => [
            {
                "id": 1,
                "name": "Devon",
                "age": 29,
                "addresses": [
                    {
                        "id": 1,
                        "city": "Seattle"
                    },
                    {
                        "id": 2,
                        "city": "Portland"
                    }
                ]
            },
            {
                "id": 2,
                "name": "Mina",
                "age": 34,
                "addresses": [
                    {
                        "id": 3,
                        "city": "San Francisco"
                    }
                ]
            },
            {
                "id": 3,
                "name": "Sachin",
                "age": 4,
                "addresses": [
                    {
                        "id": 4,
                        "city": "Mumbai"
                    },
                    {
                        "id": 5,
                        "city": "Vizag"
                    }
                ]
            }
        ]
    })
    render(<Playground />);

    expect(screen.getByRole("button", { name: "Playground" })).toBeInTheDocument();

    const user = userEvent.setup()

    expect(
        await screen.findByText(/child2 - Sachin/i, { selector: "div" })
    ).toBeInTheDocument();
})

test('calls fetchData on button click', async () => {
    const initialPeople = [
        { id: 1, name: 'Devon', age: 29, addresses: [] },
        { id: 2, name: 'Mina', age: 34, addresses: [] },
    ];
    const afterClickPeople = [{ id: 3, name: 'Zara', age: 25, addresses: [] }];

    global.fetch = jest
        .fn()
        // initial mount -> fetchData() -> GET
        .mockResolvedValueOnce({
            ok: true,
            json: async () => initialPeople,
        })
        // button click -> POST
        .mockResolvedValueOnce({
            ok: true,
            json: async () => ({}),
        })
        // button click -> fetchData() -> GET
        .mockResolvedValueOnce({
            ok: true,
            json: async () => afterClickPeople,
        });

    render(<Playground />);
    const user = userEvent.setup();

    // sanity: initial GET populated UI
    expect(await screen.findByText(/child1 - Devon/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /playground/i }));

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(3));

    // 2nd call: POST
    expect(global.fetch).toHaveBeenNthCalledWith(
        2,
        '/api/people',
        expect.objectContaining({
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
        })
    );

    // 3rd call: GET (this is fetchData being called after click)
    expect(global.fetch).toHaveBeenNthCalledWith(3, '/api/people');

    // UI updated from the 3rd call's response
    expect(await screen.findByText(/child1 - Zara/i)).toBeInTheDocument();
});

test('calls fetchData on button click (MSW)', async () => {
    // MSW-backed “server state” used by the /api/people handlers
    seedPeople([
        { id: 1, name: 'Devon', age: 29, addresses: [] },
        { id: 2, name: 'Mina', age: 34, addresses: [] },
    ]);

    render(<Playground />);
    const user = userEvent.setup();

    expect(await screen.findByText(/child1 - Devon/i)).toBeInTheDocument();

    // Clicking triggers POST then fetchData() GET, which should refresh UI from posted payload
    await user.click(screen.getByRole('button', { name: /playground/i }));

    await waitFor(() => {
        expect(screen.getByText(/child1 - Sachin/i)).toBeInTheDocument();
    });
});