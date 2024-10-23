import React, { useEffect, useState } from 'react';
import { Col, Divider, Form, Input, InputNumber, message, Modal, notification, Radio, Row, DatePicker } from 'antd';
import moment from 'moment';
import { callUpdateVoucher } from '../../../services/api';

const VoucherModalUpdate = (props) => {
    const { openModalUpdate, setOpenModalUpdate, dataUpdate, setDataUpdate, fetchVouchers } = props;
    const [isSubmit, setIsSubmit] = useState(false);
    const [form] = Form.useForm();

    useEffect(() => {
        if (dataUpdate?.id) {
            const init = {
                id: dataUpdate.id,
                voucherCode: dataUpdate.voucherCode,
                voucherValue: dataUpdate.voucherValue,
                startDate: dataUpdate.startDate ? moment(dataUpdate.startDate) : null,
                endDate: dataUpdate.endDate ? moment(dataUpdate.endDate) : null,
                active: dataUpdate.active,
                description: dataUpdate.description || '',
            };
            form.setFieldsValue(init);
        }
        return () => {
            form.resetFields();
        };
    }, [dataUpdate, form]);

    const onFinish = async (values) => {
        setIsSubmit(true);

        if (!values.voucherCode) {
            message.error('Vui lòng nhập mã voucher!');
            setIsSubmit(false);
            return;
        }

        const isoStartDate = values.startDate.toISOString();
        const isoEndDate = values.endDate.toISOString();

        try {
            const res = await callUpdateVoucher({
                voucherCode: values.voucherCode,
                voucherValue: values.voucherValue,
                description: values.description,
                startDate: isoStartDate,
                endDate: isoEndDate,
                active: values.active,
            });

            if (res && res.data) {
                message.success('Cập nhật voucher thành công');
                form.resetFields();
                setOpenModalUpdate(false);
                await fetchVouchers();
            } else {
                notification.error({
                    message: 'Đã có lỗi xảy ra',
                    description: res.message,
                });
            }
        } catch (error) {
            notification.error({
                message: 'Lỗi khi cập nhật voucher',
                description: error.message,
            });
        } finally {
            setIsSubmit(false);
        }
    };

    return (
        <Modal
            title="Cập nhật voucher"
            open={openModalUpdate}
            onOk={() => form.submit()}
            onCancel={() => {
                form.resetFields();
                setOpenModalUpdate(false);
                setDataUpdate(null);
            }}
            okText="Cập nhật"
            cancelText="Hủy"
            confirmLoading={isSubmit}
            centered
        >
            <Divider />
            <Form
                form={form}
                name="voucherForm"
                onFinish={onFinish}
                autoComplete="off"
            >
                <Row gutter={15}>
                    <Col xs={24} sm={12}>
                        <Form.Item 
                            label="Mã voucher" 
                            name="voucherCode" 
                            rules={[{ required: true, message: 'Vui lòng nhập mã voucher!' }]}
                        >
                            <Input />
                        </Form.Item>
                    </Col>
                    <Col xs={24} sm={12}>
                        <Form.Item 
                            label="Giảm giá (%)" 
                            name="voucherValue" 
                            rules={[{ required: true, message: 'Vui lòng nhập giảm giá!' }]}
                        >
                            <InputNumber min={0} style={{ width: '100%' }} />
                        </Form.Item>
                    </Col>
                    <Col xs={24} sm={12}>
                        <Form.Item 
                            label="Ngày bắt đầu" 
                            name="startDate" 
                            rules={[{ required: true, message: 'Vui lòng chọn ngày bắt đầu!' }]}
                        >
                            <DatePicker style={{ width: '100%' }} />
                        </Form.Item>
                    </Col>
                    <Col xs={24} sm={12}>
                        <Form.Item 
                            label="Ngày kết thúc" 
                            name="endDate" 
                            rules={[{ required: true, message: 'Vui lòng chọn ngày kết thúc!' }]}
                        >
                            <DatePicker style={{ width: '100%' }} />
                        </Form.Item>
                    </Col>
                    <Col xs={24} sm={12}>
                        <Form.Item 
                            label="Active" 
                            name="active" 
                            rules={[{ required: true, message: 'Vui lòng chọn!' }]}
                        >
                            <Radio.Group>
                                <Radio value={true}>True</Radio>
                                <Radio value={false}>False</Radio>
                            </Radio.Group>
                        </Form.Item>
                    </Col>
                    <Col xs={24} sm={12}>
                        <Form.Item 
                            label="Mô tả" 
                            name="description" 
                        >
                            <Input.TextArea rows={4} />
                        </Form.Item>
                    </Col>
                </Row>
            </Form>
        </Modal>
    );
};

export default VoucherModalUpdate;