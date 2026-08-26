import { beforeAll } from 'vitest';
import axios from 'axios';

beforeAll(() => {
  window.location.href = 'https://jhipster.tech/';

  // Make sure axios is never executed.
  axios.interceptors.request.use(request => {
    throw new Error(`Error axios should be mocked ${request.url}`);
  });
});
