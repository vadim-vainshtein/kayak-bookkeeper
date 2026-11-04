export default function MemberPicker({ people, members, setMembers, retainInactive = false }) {
    const update = (id, key, value) => {
        const next = { ...members };
        if (key === 'member') {
            if (value) next[id] = { skirt: false, paddle: false, kayak: false };
            else delete next[id];
        } else next[id] = { ...next[id], [key]: value };
        setMembers(next);
    };
    return (
        <fieldset className="member-picker">
            <legend>Участники</legend>
            {people
                .filter((person) => person.active || (retainInactive && members[person.id]))
                .map((person) => {
                    const member = members[person.id];
                    return (
                        <div className="member-option" key={person.id}>
                            <label className="checkbox">
                                <input
                                    type="checkbox"
                                    checked={Boolean(member)}
                                    onChange={(event) => update(person.id, 'member', event.target.checked)}
                                />
                                {person.fullName}
                                {!person.active && ' · неактивен'}
                            </label>
                            {member && (
                                <div className="equipment">
                                    <label className="checkbox">
                                        <input
                                            type="checkbox"
                                            checked={member.skirt}
                                            disabled={!person.active}
                                            onChange={(event) =>
                                                update(person.id, 'skirt', event.target.checked)
                                            }
                                        />
                                        Юбка
                                    </label>
                                    <label className="checkbox">
                                        <input
                                            type="checkbox"
                                            checked={member.paddle}
                                            disabled={!person.active}
                                            onChange={(event) =>
                                                update(person.id, 'paddle', event.target.checked)
                                            }
                                        />
                                        Весло
                                    </label>
                                    <label className="checkbox">
                                        <input
                                            type="checkbox"
                                            checked={member.kayak}
                                            disabled={!person.active}
                                            onChange={(event) =>
                                                update(person.id, 'kayak', event.target.checked)
                                            }
                                        />
                                        Каяк
                                    </label>
                                </div>
                            )}
                        </div>
                    );
                })}
        </fieldset>
    );
}
