/** "No students", "1 student", "N students". */
export function studentsText(count: number): string {
  return count === 0 ? 'No students' : count === 1 ? '1 student' : `${count} students`;
}
