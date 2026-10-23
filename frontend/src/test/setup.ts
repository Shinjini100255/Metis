import '@testing-library/jest-dom/vitest';
import { afterEach,vi } from 'vitest';
import { cleanup } from '@testing-library/react';
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();localStorage.clear();});
class ResizeObserverStub {observe(){} unobserve(){} disconnect(){}}
vi.stubGlobal('ResizeObserver',ResizeObserverStub);
