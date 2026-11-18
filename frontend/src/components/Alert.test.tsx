import { render, screen } from '@testing-library/react';
import { Alert } from './Alert';
import { Spinner } from './Spinner';

describe('Alert', () => {
  it('announces an error with role alert', () => {
    render(<Alert variant="error">Chyba</Alert>);
    expect(screen.getByRole('alert')).toHaveTextContent('Chyba');
  });

  it('uses role status and the info variant by default', () => {
    render(<Alert>Info</Alert>);
    expect(screen.getByRole('status')).toHaveTextContent('Info');
  });
});

describe('Spinner', () => {
  it('shows the default and a custom label', () => {
    const { rerender } = render(<Spinner />);
    expect(screen.getByRole('status')).toHaveTextContent('Načítava sa…');
    rerender(<Spinner label="Počkaj" />);
    expect(screen.getByRole('status')).toHaveTextContent('Počkaj');
  });
});