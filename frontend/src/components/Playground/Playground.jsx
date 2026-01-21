import React, { lazy, Suspense, useCallback, useState } from 'react';
import Child1 from './Child1';
const Child2 = lazy(() => import('./Child2'));

const Playground = () => {
    let [arr1, setArr1] = useState([{
        name: "Srikar",
        age: "1"
    }, {
        name: "Charan",
        age: "2"
    }]);
    const fn = useCallback(() => { }, [])
    return <>
        <button onClick={() => setArr1([
            ...arr1,
            {
                name: "Suri",
                age: "3"
            }])} >
            Playground
        </button>
        {arr1.map((item, i) => <Child1 fn={fn} key={i} item={item} />)}
        {arr1.map((item, i) => <Suspense fallback={"loading"}>
            <Child2 fn={fn} key={i} item={item} />
        </Suspense>)}
    </>
}

export default Playground;