import { renderHook, act } from '@testing-library/react';
import { useAuth } from './useAuth';

function setStoredUser(key: 'user' | 'merchantUser', value: unknown) {
  localStorage.setItem(key, JSON.stringify(value));
}

describe('useAuth', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('defaults to an empty, unauthenticated user when nothing is stored', () => {
    const { result } = renderHook(() => useAuth('admin'));

    expect(result.current.user).toEqual({});
    expect(result.current.privileges).toEqual([]);
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.hasPrivilege('ACCESS_TRANSACTION_LOG')).toBe(false);
  });

  it('reads the admin user from the "user" localStorage key', () => {
    setStoredUser('user', {
      name: 'Ada Admin',
      email: 'ada@example.com',
      privileges: [{ privilege: 'ACCESS_TRANSACTION_LOG' }, { privilege: 'ACCESS_ADMIN' }],
    });

    const { result } = renderHook(() => useAuth('admin'));

    expect(result.current.user.name).toBe('Ada Admin');
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.privileges).toEqual(['ACCESS_TRANSACTION_LOG', 'ACCESS_ADMIN']);
    expect(result.current.hasPrivilege('ACCESS_TRANSACTION_LOG')).toBe(true);
    expect(result.current.hasPrivilege('DELETE_ADMIN')).toBe(false);
  });

  it('reads the merchant user from the separate "merchantUser" key', () => {
    setStoredUser('user', { name: 'Admin User', privileges: [{ privilege: 'ACCESS_ADMIN' }] });
    setStoredUser('merchantUser', { name: 'Merchant User', privileges: [{ privilege: 'SEND_SMS' }] });

    const admin = renderHook(() => useAuth('admin'));
    const merchant = renderHook(() => useAuth('merchant'));

    expect(admin.result.current.user.name).toBe('Admin User');
    expect(admin.result.current.hasPrivilege('SEND_SMS')).toBe(false);
    expect(merchant.result.current.user.name).toBe('Merchant User');
    expect(merchant.result.current.hasPrivilege('SEND_SMS')).toBe(true);
  });

  it('tolerates malformed JSON in storage instead of throwing', () => {
    localStorage.setItem('user', '{not valid json');

    const { result } = renderHook(() => useAuth('admin'));

    expect(result.current.user).toEqual({});
    expect(result.current.hasPrivilege('ANYTHING')).toBe(false);
  });

  it('refresh() re-reads localStorage after an external write', () => {
    const { result } = renderHook(() => useAuth('admin'));
    expect(result.current.isAuthenticated).toBe(false);

    act(() => {
      setStoredUser('user', { name: 'Late Login', privileges: [] });
      result.current.refresh();
    });

    expect(result.current.user.name).toBe('Late Login');
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('clear() removes the stored user and resets state to {}', () => {
    setStoredUser('user', { name: 'Ada Admin', privileges: [{ privilege: 'ACCESS_ADMIN' }] });
    const { result } = renderHook(() => useAuth('admin'));
    expect(result.current.isAuthenticated).toBe(true);

    act(() => {
      result.current.clear();
    });

    expect(result.current.user).toEqual({});
    expect(result.current.isAuthenticated).toBe(false);
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('re-reads on a "storage" event for its own key (cross-tab login/logout)', () => {
    const { result } = renderHook(() => useAuth('admin'));

    act(() => {
      setStoredUser('user', { name: 'Other Tab User', privileges: [] });
      window.dispatchEvent(new StorageEvent('storage', { key: 'user' }));
    });

    expect(result.current.user.name).toBe('Other Tab User');
  });
});
