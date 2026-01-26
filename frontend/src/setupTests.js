import '@testing-library/jest-dom';

import 'whatwg-fetch';

import { TextDecoder, TextEncoder } from 'util';

global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;

// MSW needs TextEncoder available at import time in this environment.
// Use require() to ensure polyfills are applied first.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { server } = require('./mocks/server');

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
