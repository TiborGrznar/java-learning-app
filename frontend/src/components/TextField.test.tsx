import { fireEvent, render, screen } from '@testing-library/react';
import { TextField } from './TextField';

describe('TextField', () => {
  it('links the label to the input', () => {
    render(<TextField label="E-mail" />);
    expect(screen.getByLabelText('E-mail')).toBeInTheDocument();
  });

  it('marks the input invalid and describes it with the error and the hint', () => {
    render(<TextField label="E-mail" hint="Použi školský e-mail" error="Povinné pole" />);
    const input = screen.getByLabelText('E-mail');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Povinné pole Použi školský e-mail');
  });

  it('is not marked invalid without an error', () => {
    render(<TextField label="E-mail" />);
    expect(screen.getByLabelText('E-mail')).not.toHaveAttribute('aria-invalid');
  });

  it('passes native props through and reports changes', () => {
    const onChange = vi.fn();
    render(<TextField label="E-mail" type="email" onChange={onChange} />);
    const input = screen.getByLabelText('E-mail');
    expect(input).toHaveAttribute('type', 'email');
    fireEvent.change(input, { target: { value: 'a@b.sk' } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});