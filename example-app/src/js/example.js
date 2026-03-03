import { Otp } from 'capacitor-otp';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    Otp.echo({ value: inputValue })
}
