import React, { useState } from "react";

const Child1 = (props) => {
    let [arr1, setArr1] = useState([{
        name: "Srikar",
        age: "1"
    }]);
    console.log(props.item?.name);

    return <div>
        <button onClick={() => setArr1([
            ...arr1,
            {
                name: "Suri",
                age: "2"
            }])} >
            Child
        </button>
        {`child1 - ${props.item?.name}`}
    </div>
}

export default React.memo(Child1);