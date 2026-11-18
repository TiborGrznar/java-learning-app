import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetSessionForTests, useSession } from '../../api/session';
import { userFixture } from '../../test/fetchMock';
import { HomePage } from './HomePage';

describe('HomePage', () => {
  beforeEach(() => {
    resetSessionForTests();
  });

  it('greets the user by name and role', () => {
    useSession.setState({ status: 'authenticated', role: 'STUDENT', user: { ...userFixture } });
    render(<HomePage />);

    expect(screen.getByRole('heading', { name: 'Vitajte, Jana' })).toBeInTheDocument();
    expect(screen.getByText('Prihlásený ako študent.')).toBeInTheDocument();
  });

  it('shows nothing without a user', () => {
    const { container } = render(<HomePage />);
    expect(container).toBeEmptyDOMElement();
  });
});