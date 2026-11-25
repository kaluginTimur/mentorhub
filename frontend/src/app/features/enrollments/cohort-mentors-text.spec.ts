import {
  activeFirst,
  fitCount,
  loadText,
  markWords,
  mentorsText,
  summaryText,
} from './cohort-mentors-text';

const mentor = (name: string, disabled = false, mentorRole = true) => ({
  id: name,
  name,
  email: `${name}@x.test`,
  disabled,
  mentorRole,
});

describe('fitCount', () => {
  it('shows every name that fits without the "+k" button', () => {
    expect(fitCount([100, 100, 100], 40, 300)).toBe(3);
  });

  it('leaves room for the "+k" button when some names do not fit', () => {
    expect(fitCount([100, 100, 100], 40, 250)).toBe(2);
    expect(fitCount([100, 100, 100], 40, 240)).toBe(2);
    expect(fitCount([100, 100, 100], 40, 239)).toBe(1);
  });

  it('always shows at least one name', () => {
    expect(fitCount([400, 100], 40, 200)).toBe(1);
  });
});

describe('the words about the mentors of a cohort', () => {
  it('writes plurals, with one and zero as the mockup does', () => {
    expect(mentorsText(0)).toBe('0 mentors');
    expect(mentorsText(1)).toBe('1 mentor');
    expect(mentorsText(2)).toBe('2 mentors');
    expect(loadText({ students: 1, cohorts: 0 })).toBe('1 student · 0 cohorts');
    expect(loadText({ students: 3, cohorts: 1 })).toBe('3 students · 1 cohort');
  });

  it('counts a disabled mentor and one without the role as inactive in the summary, once each', () => {
    const mentors = [
      mentor('a'),
      mentor('b', true),
      mentor('c', false, false),
      mentor('d', true, false),
    ];
    expect(summaryText(mentors)).toBe('4 mentors · 3 inactive');
    expect(summaryText([mentor('a')])).toBe('1 mentor');
  });

  it('names every mark of a mentor, in order, and none for an active one', () => {
    expect(markWords(mentor('a'))).toBe('');
    expect(markWords(mentor('a', true))).toBe(', Disabled');
    expect(markWords(mentor('a', false, false))).toBe(', No mentor role');
    expect(markWords(mentor('a', true, false))).toBe(', Disabled, No mentor role');
  });

  it('puts inactive mentors last, keeps the order inside each group and leaves the input alone', () => {
    const input = [mentor('a', true), mentor('b'), mentor('c', false, false), mentor('d')];
    expect(activeFirst(input).map((m) => m.name)).toEqual(['b', 'd', 'a', 'c']);
    expect(input.map((m) => m.name)).toEqual(['a', 'b', 'c', 'd']);
  });
});
