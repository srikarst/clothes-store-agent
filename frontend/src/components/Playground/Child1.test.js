import { render, screen, waitFor } from '@testing-library/react';

import Child1 from './Child1';

test('loads Child1', () => {
    render(<Child1 />);

    expect(screen.getByRole("button", {name: "Child"})).toBeInTheDocument();
})