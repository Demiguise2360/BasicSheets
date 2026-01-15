let pdfDoc = null;
let pageNum = 1;
let canvas = null;
let ctx = null;

window.loadPdf = async function (url) {
    const pdfjsLib = window['pdfjs-dist/build/pdf'];
    pdfjsLib.GlobalWorkerOptions.workerSrc =
        'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

    pdfDoc = await pdfjsLib.getDocument(url).promise;
    return pdfDoc.numPages;
};

window.renderPage = async function (num) {
    pageNum = num;

    const page = await pdfDoc.getPage(num);
    const viewport = page.getViewport({ scale: 1.5 });

    canvas.height = viewport.height;
    canvas.width = viewport.width;

    await page.render({
        canvasContext: ctx,
        viewport: viewport
    }).promise;
};

window.renderTwoPage = async function (num) {
    pageNum = num;

    const page1 = await pdfDoc.getPage(num);
    const viewport1 = page1.getViewport({ scale: 1.5 });
    const temp1 = document.createElement('canvas');
    temp1.width = Math.ceil(viewport1.width);
    temp1.height = Math.ceil(viewport1.height);
    const tempCtx1 = temp1.getContext('2d');

    await page1.render({
        canvasContext: tempCtx1,
        viewport: viewport1
    }).promise;

    let temp2 = null;
    if (num + 1 <= pdfDoc.numPages) {
        const page2 = await pdfDoc.getPage(num + 1);
        const viewport2 = page2.getViewport({ scale: 1.5 });
        temp2 = document.createElement('canvas');
        temp2.width = Math.ceil(viewport2.width);
        temp2.height = Math.ceil(viewport2.height);
        const tempCtx2 = temp2.getContext('2d');

        await page2.render({
            canvasContext: tempCtx2,
            viewport: viewport2
        }).promise;
    }

    if (temp2) {
        const gap = 16;
        const separatorWidth = 5;
        const separatorColor = "#e0e0e0";

        const totalWidth = temp1.width + gap + temp2.width;
        const maxHeight = Math.max(temp1.height, temp2.height);

        canvas.width = totalWidth;
        canvas.height = maxHeight;

        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.drawImage(temp1, 0, 0);

        const separatorX = temp1.width + Math.floor((gap - separatorWidth) / 2);
        ctx.fillStyle = separatorColor;
        ctx.fillRect(separatorX, 0, separatorWidth, canvas.height);

        ctx.drawImage(temp2, temp1.width + gap, 0);
    } else {
        canvas.width = temp1.width;
        canvas.height = temp1.height;

        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(temp1, 0, 0);
    }
};

window.initCanvas = function (id) {
    canvas = document.getElementById(id);
    ctx = canvas.getContext("2d");
};

window.getWindowWidth = function () {
    return window.innerWidth;
};