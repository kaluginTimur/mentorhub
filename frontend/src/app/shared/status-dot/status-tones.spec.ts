import { INVITATION_STATUS } from '../../core/api/models/invitation-status-array';
import { USER_STATUS } from '../../core/api/models/user-status-array';
import { INVITATION_STATUS_TONES } from '../../features/invitations/labels';
import { STATUS_TONES } from '../../features/users/labels';

describe('status tones', () => {
  it('gives every user status its tone', () => {
    expect(Object.keys(STATUS_TONES).sort()).toEqual([...USER_STATUS].sort());
    expect(STATUS_TONES).toEqual({ ACTIVE: 'success', DISABLED: 'neutral' });
  });

  it('gives every invitation status its tone', () => {
    expect(Object.keys(INVITATION_STATUS_TONES).sort()).toEqual([...INVITATION_STATUS].sort());
    expect(INVITATION_STATUS_TONES).toEqual({
      PENDING: 'info',
      ACCEPTED: 'success',
      EXPIRED: 'neutral',
      REVOKED: 'neutral',
    });
  });
});
