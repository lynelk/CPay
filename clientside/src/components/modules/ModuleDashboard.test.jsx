import { dashboardErrorDetails } from './ModuleDashboard';

describe('dashboardErrorDetails', () => {
  it('uses Spring error JSON instead of showing Error undefined', () => {
    expect(
      dashboardErrorDetails({
        status: 500,
        error: 'Internal Server Error',
        path: '/transactions/getDashboardDetailsNetworkBalances',
      })
    ).toEqual({
      title: 'Error',
      message: 'Internal Server Error',
    });
  });

  it('keeps existing app error codes readable', () => {
    expect(
      dashboardErrorDetails({
        code: '107',
        message: 'You are not logged in. Login first.',
      })
    ).toEqual({
      title: 'Error 107',
      message: 'You are not logged in. Login first.',
    });
  });
});
