export function attendanceMarks(members, draft) {
    return members
        .filter(
            (entry) =>
                entry.status === 'PLANNED' ||
                (draft[entry.personId] ?? entry.status !== 'ABSENT') !== (entry.status === 'PRESENT'),
        )
        .map((entry) => ({
            personId: entry.personId,
            status: (draft[entry.personId] ?? entry.status !== 'ABSENT') ? 'PRESENT' : 'ABSENT',
        }))
        .sort((left, right) => (left.status === right.status ? 0 : left.status === 'ABSENT' ? -1 : 1));
}
