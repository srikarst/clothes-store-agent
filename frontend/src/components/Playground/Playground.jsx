import React, { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import Child1 from './Child1';
import { SAMPLE_PEOPLE } from './samplePeople';
const Child2 = lazy(() => import('./Child2'));

const Playground = () => {
    const [arr1, setArr1] = useState([]);
    const fetchData = useCallback(async () => {
        try {
            const res = await fetch('/api/people');
            const data = await res.json();
            setArr1(Array.isArray(data) ? data : (data?.people ?? []));
        } catch (e) {
            setArr1([]);
        }
    }, [])
    const fn = useCallback(() => { }, [])
    useEffect(() => {
        fetchData();
    }, [fetchData])
    return <>
        <button onClick={async () => {
            await fetch('/api/people', {
                method: "POST",
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(SAMPLE_PEOPLE)
            })
            fetchData();
        }} >
            Playground
        </button>
        {arr1.map((item, i) => <Child1 fn={fn} key={i} item={item} />)}
        {arr1.map((item, i) => <Suspense key={i} fallback={"loading"}>
            <Child2 fn={fn} item={item} />
        </Suspense>)}
    </>
}

export default Playground;